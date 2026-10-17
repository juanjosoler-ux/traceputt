package es.vigilanciacv.personal;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.*;
import android.util.Log;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class VigilanceService extends Service {
    static final String START="es.vigilanciacv.personal.START";
    static final String CHECK="es.vigilanciacv.personal.CHECK";
    static final String STOP="es.vigilanciacv.personal.STOP";
    private static final String CHANNEL="vigilancia_cv",ALARM="vigilancia_cv_avisos";
    private static final String RAINS="https://saih.chj.es/lluvias";
    private static final String FLOWS="https://saih.chj.es/mapa-aforos";
    private static final String RSS="https://www.aemet.es/documentos_d/eltiempo/prediccion/avisos/rss/CAP_AFAZ%s_RSS.xml";
    private volatile boolean running=false,busy=false;
    private Thread worker;
    private android.content.SharedPreferences prefs;
    private final long INTERVAL=300000L;
    private final TimeZone TZ=TimeZone.getTimeZone("Europe/Madrid");

    @Override public void onCreate() {
        super.onCreate();
        prefs=getSharedPreferences("vigilancia",MODE_PRIVATE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service=new NotificationChannel(CHANNEL,"Vigilancia activa",NotificationManager.IMPORTANCE_LOW);
        NotificationChannel alarm=new NotificationChannel(ALARM,"Alertas de lluvia e inundación",NotificationManager.IMPORTANCE_HIGH);
        alarm.enableVibration(true);
        nm.createNotificationChannel(service);nm.createNotificationChannel(alarm);
    }
    Notification foreground(){
        Intent intent=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle("Vigilancia CV").setContentText("Monitorizando Casa y Apartamento cada 5 minutos")
          .setOngoing(true).setContentIntent(pi).build();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        try {
            if(Build.VERSION.SDK_INT>=29)startForeground(1,foreground(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(1,foreground());
        }catch(Exception ex){prefs.edit().putString("last_check","No se pudo iniciar el servicio: "+ex.getMessage()).apply();stopSelf();return START_NOT_STICKY;}
        if(!running){
            running=true;prefs.edit().putBoolean("monitoring",true).apply();
            worker=new Thread(()->{
                while(running){
                    long wait=INTERVAL-(System.currentTimeMillis()-prefs.getLong("last_poll_ms",0));
                    if(wait>0){try{Thread.sleep(Math.max(1500,wait));}catch(InterruptedException e){if(!running)break;} continue;}
                    cycle();
                }
            },"vigilancia-poll");
            worker.start();
        } else if(i!=null&&CHECK.equals(i.getAction())&&worker!=null)worker.interrupt();
        return START_STICKY;
    }
    @Override public void onDestroy(){
        running=false;prefs.edit().putBoolean("monitoring",false).apply();
        if(worker!=null)worker.interrupt();super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
    static String http(String url,int timeout) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(timeout*1000);c.setReadTimeout(timeout*1000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","VigilanciaCVPersonal/0.1 (Android; aplicación personal, OpenStreetMap Nominatim)");
        c.setRequestProperty("Accept","application/xml,text/html,application/json,*/*");
        try {
            if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;int total=0;
                while((n=in.read(b))!=-1){total+=n;if(total>8000000)throw new IOException("Respuesta demasiado grande");out.write(b,0,n);}
                return out.toString("UTF-8");
            }
        }finally{c.disconnect();}
    }
    static JSONArray jsArray(String html,String var) throws Exception {
        Matcher m=Pattern.compile("\\b(?:let|const|var)\\s+"+Pattern.quote(var)+"\\s*=\\s*\\[").matcher(html);
        if(!m.find())throw new Exception("CHJ: no se encuentra let "+var);
        int at=m.end()-1,depth=0;char quote=0;boolean esc=false;
        for(int j=at;j<html.length();j++){
            char c=html.charAt(j);
            if(quote!=0){if(esc){esc=false;continue;}if(c=='\\'){esc=true;continue;}if(c==quote)quote=0;continue;}
            if(c=='"'||c=='\''){quote=c;continue;}
            if(c=='[')depth++;else if(c==']'&&--depth==0)return new JSONArray(html.substring(at,j+1));
        }
        throw new Exception("Array JavaScript incompleto: "+var);
    }
    static Double num(Object value){
        if(value==null||JSONObject.NULL.equals(value))return null;
        try{String s=String.valueOf(value).trim().replace(",",".");if(s.isEmpty()||s.equals("--"))return null;
            double d=Double.parseDouble(s);return Double.isFinite(d)?d:null;
        }catch(Exception e){return null;}
    }
    static double val(JSONObject j,String key){Double v=num(j.opt(key));return v==null?Double.NaN:v;}
    static String text(JSONObject j,String key){return j.optString(key,"").trim();}
    static double[] utm(double x,double y){
        double a=6378137,e2=.00669438002290,ep2=e2/(1-e2),k0=.9996;
        double xx=x-500000,m=y/k0,mu=m/(a*(1-e2/4-3*e2*e2/64-5*Math.pow(e2,3)/256));
        double e1=(1-Math.sqrt(1-e2))/(1+Math.sqrt(1-e2));
        double phi=mu+(3*e1/2-27*Math.pow(e1,3)/32)*Math.sin(2*mu)
           +(21*e1*e1/16-55*Math.pow(e1,4)/32)*Math.sin(4*mu)
           +(151*Math.pow(e1,3)/96)*Math.sin(6*mu)+(1097*Math.pow(e1,4)/512)*Math.sin(8*mu);
        double sp=Math.sin(phi),cp=Math.cos(phi),tp=Math.tan(phi);
        double n1=a/Math.sqrt(1-e2*sp*sp),r1=a*(1-e2)/Math.pow(1-e2*sp*sp,1.5),t1=tp*tp,c1=ep2*cp*cp,d=xx/(n1*k0);
        double lat=phi-(n1*tp/r1)*(d*d/2-(5+3*t1+10*c1-4*c1*c1-9*ep2)*Math.pow(d,4)/24+
                    (61+90*t1+298*c1+45*t1*t1-252*ep2-3*c1*c1)*Math.pow(d,6)/720);
        double lon=-3*Math.PI/180+(d-(1+2*t1+c1)*Math.pow(d,3)/6+
                    (5-2*c1+28*t1-3*c1*c1+8*ep2+24*t1*t1)*Math.pow(d,5)/120)/cp;
        return new double[]{Math.toDegrees(lat),Math.toDegrees(lon)};
    }
    static double[] coords(JSONObject ob,boolean isFlow){
        double lat=val(ob,"fldNCoordGPSLat"),lon=val(ob,"fldNCoordGPSLon");
        if(lat>=35&&lat<=44&&lon>=-3&&lon<=2)return new double[]{lat,lon};
        if(!isFlow)return new double[]{Double.NaN,Double.NaN};
        String[][] keys={{"fldNCoordUTMX","fldNCoordUTMY"},{"fldNCoordX","fldNCoordY"},{"fldNUTMX","fldNUTMY"},
           {"coordX","coordY"},{"fldNCoordenadaX","fldNCoordenadaY"}};
        for(String[] pair:keys){
            double x=val(ob,pair[0]),y=val(ob,pair[1]);
            if(x>=100000&&x<=900000&&y>=3800000&&y<=4900000)return utm(x,y);
        }
        Iterator<String> it=ob.keys();double x=Double.NaN,y=Double.NaN;
        while(it.hasNext()){
            String key=it.next(),k=key.toLowerCase(Locale.ROOT);
            double v=val(ob,key);
            if(v>=100000&&v<=900000&&(k.endsWith("x")||k.contains("este")||k.contains("easting")))x=v;
            if(v>=3800000&&v<=4900000&&(k.endsWith("y")||k.contains("norte")||k.contains("northing")))y=v;
        }
        return Double.isFinite(x)&&Double.isFinite(y)?utm(x,y):new double[]{Double.NaN,Double.NaN};
    }
    static JSONArray normalize(JSONArray raw,boolean flow){
        JSONArray out=new JSONArray();
        for(int i=0;i<raw.length();i++){
            JSONObject r=raw.optJSONObject(i);if(r==null)continue;
            if(!flow&&(!r.has("fldTEstado")||r.isNull("fldTEstado")||text(r,"fldTEstado").isEmpty()))continue;
            String name=text(r,"fldTNombre");if(name.isEmpty())continue;
            JSONObject j=new JSONObject();
            try{
                double[] c=coords(r,flow);
                j.put("nombre",name); if(Double.isFinite(c[0])&&Double.isFinite(c[1])){j.put("lat",c[0]);j.put("lon",c[1]);}
                if(flow){
                    j.put("valor",r.opt("fldNUltimoValor"));j.put("amarillo",r.opt("fldFUmbralBajo"));
                    j.put("naranja",r.opt("fldFUmbralMedio"));j.put("rojo",r.opt("fldFUmbralAlto"));
                    j.put("fecha",r.opt("fldDFechaComunicacion"));
                } else {
                    j.put("h1",r.opt("lluvia_1h"));j.put("h4",r.opt("lluvia_4h"));
                    j.put("h12",r.opt("lluvia_12h"));j.put("h24",r.opt("lluvia_24h"));
                    j.put("fecha",r.opt("fecha_1h"));
                }
                out.put(j);
            }catch(Exception ignored){}
        }
        return out;
    }
    static Document parseXML(String src)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
    }
    static String tag(Element parent,String name){
        NodeList nl=parent.getElementsByTagNameNS("*",name);
        if(nl.getLength()==0)nl=parent.getElementsByTagName(name);
        return nl.getLength()==0?"":nl.item(0).getTextContent().trim();
    }
    static Instant instant(String s){
        try{return OffsetDateTime.parse(s).toInstant();}catch(Exception ignored){}
        try{return Instant.parse(s);}catch(Exception ignored){}
        return null;
    }
    static Long parseTime(Object date){
        if(date==null||JSONObject.NULL.equals(date))return null;
        String s=String.valueOf(date).trim();
        Instant iso=instant(s);if(iso!=null)return iso.toEpochMilli();
        for(String fmt:new String[]{"dd/MM/yyyy HH:mm:ss","dd/MM/yyyy HH:mm","yyyy-MM-dd HH:mm:ss","dd-MM-yyyy HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss"}){
            try{SimpleDateFormat f=new SimpleDateFormat(fmt,new Locale("es","ES"));f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Europe/Madrid"));
                return f.parse(s).getTime();}catch(Exception ignored){}
        }
        return null;
    }
    static boolean fresh(JSONObject j){
        Long t=parseTime(j.opt("fecha"));
        return t!=null&&t<=System.currentTimeMillis()+120000&&System.currentTimeMillis()-t<=45*60000L;
    }
    static HashMap<String,JSONObject> index(JSONArray arr){
        HashMap<String,JSONObject> map=new HashMap<>();
        for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x!=null)map.put(x.optString("nombre"),x);}
        return map;
    }
    static boolean contains(JSONArray a,String n){for(int i=0;a!=null&&i<a.length();i++)if(n.equals(a.optString(i)))return true;return false;}
    static String time(){return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",new Locale("es","ES")).format(new Date());}

    static class Warnings {
        int active=0;
        final ArrayList<String> current=new ArrayList<>(),future=new ArrayList<>();
        boolean valid=false;
    }
    Warnings readAemet(String code) throws Exception{
        Warnings w=new Warnings();
        Document feed=parseXML(http(String.format(Locale.ROOT,RSS,code),15));
        NodeList items=feed.getElementsByTagName("item");
        if(items.getLength()>40)throw new IOException("RSS demasiado grande para comprobar todos los CAP");
        int examined=0;boolean incomplete=false;
        for(int i=0;i<items.getLength();i++){
            Element item=(Element)items.item(i);
            String link=tag(item,"link");
            if(!link.startsWith("https://www.aemet.es/documentos_d/eltiempo/prediccion/avisos/cap/")||!link.endsWith(".xml"))continue;
            examined++;
            try{
                Document cap=parseXML(http(link,12));
                NodeList info=cap.getElementsByTagNameNS("*","info");
                for(int k=0;k<info.getLength();k++){
                    Element el=(Element)info.item(k);
                    if(!tag(el,"language").startsWith("es"))continue;
                    String event=tag(el,"event"),severity=tag(el,"severity"),onset=tag(el,"onset"),expires=tag(el,"expires");
                    Instant begin=instant(onset),end=instant(expires);
                    if(begin==null||end==null)throw new IOException("CAP sin tiempo válido");
                    if(Instant.now().isAfter(end))break;
                    String level=severity.equalsIgnoreCase("Extreme")?"rojo":severity.equalsIgnoreCase("Severe")?"naranja":severity.equalsIgnoreCase("Moderate")?"amarillo":"normal";
                    NodeList pars=el.getElementsByTagNameNS("*","parameter");
                    for(int t=0;t<pars.getLength();t++){
                        Element par=(Element)pars.item(t);
                        if(tag(par,"valueName").contains("Meteoalerta nivel")){level=tag(par,"value").toLowerCase(Locale.ROOT);break;}
                    }
                    int lv=level.contains("rojo")?3:level.contains("naranja")?2:level.contains("amarillo")?1:0;
                    String timing=java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Madrid")).format(begin);
                    String msg=event+" "+level+" (desde "+timing+")";
                    if(Instant.now().isBefore(begin))w.future.add(msg);else{w.active=Math.max(w.active,lv);w.current.add(msg);}
                    break;
                }
            }catch(Exception e){incomplete=true;Log.w("VigilanciaCV","CAP individual sin datos: "+e.getMessage());}
        }
        if(incomplete)throw new IOException("Algunos CAP no pudieron validarse");
        w.valid=true;return w;
    }
    static class Source {
        JSONArray data=new JSONArray();boolean ok=false;String error="";
    }
    Source source(String url,String var,boolean flow){
        Source s=new Source();
        try{s.data=normalize(jsArray(http(url,18),var),flow);s.ok=s.data.length()>0;
            if(!s.ok)s.error="CHJ no devolvió estaciones utilizables";
        }catch(Exception e){s.error=e.getMessage();}
        return s;
    }
    private void cycle(){
        if(busy)return;busy=true;
        long epoch=System.currentTimeMillis();
        prefs.edit().putLong("last_poll_ms",epoch).putString("last_check",time()).apply();
        try{
            Source rain=source(RAINS,"estaciones",false),flows=source(FLOWS,"aforos",true);
            if(rain.ok)prefs.edit().putString("catalog_rains",rain.data.toString()).apply();
            if(flows.ok)prefs.edit().putString("catalog_flows",flows.data.toString()).apply();
            HashMap<String,JSONObject> rmap=index(rain.data),fmap=index(flows.data);
            JSONArray places=new JSONArray(prefs.getString("places","[]"));
            for(int i=0;i<places.length();i++){
                JSONObject place=places.optJSONObject(i);if(place==null)continue;
                String name=place.optString("nombre","Lugar");
                Warnings w=new Warnings();String aeError="";
                try{w=readAemet(place.optString("codigo"));}catch(Exception e){aeError=e.getMessage();}
                JSONObject result=score(place,rmap,fmap,rain,flows,w,aeError);
                prefs.edit().putString("result_"+i,result.toString()).apply();
                alert(i,name,result);
            }
            prefs.edit().putString("chj_diagnostic","Lluvias: "+(rain.ok?rain.data.length()+" estaciones":"ERROR "+rain.error)
                  +" | Aforos: "+(flows.ok?flows.data.length()+" estaciones":"ERROR "+flows.error)).apply();
        }catch(Exception e){prefs.edit().putString("last_check","Error: "+e.getMessage()).apply();Log.e("VigilanciaCV","Error general",e);}
        finally{busy=false;}
    }
    private JSONObject score(JSONObject place,Map<String,JSONObject> rain,Map<String,JSONObject> flows,Source rs,Source fs,Warnings aemet,String aeError){
        JSONObject out=new JSONObject();
        StringBuilder desc=new StringBuilder(),missing=new StringBuilder();
        int level=aemet.active;
        boolean complete=aemet.valid;
        if(!aemet.valid)missing.append("AEMET sin datos. ");
        JSONArray gauges=place.optJSONArray("pluviometros"),up=place.optJSONArray("cabecera"),af=place.optJSONArray("aforos");
        if(gauges==null||gauges.length()==0){complete=false;missing.append("Elige pluviómetros. ");}
        if(!rs.ok){complete=false;missing.append("SAIH lluvias sin datos. ");}
        if(!fs.ok&&af!=null&&af.length()>0){complete=false;missing.append("SAIH aforos sin datos. ");}
        String risks=place.optJSONArray("riesgos")==null?"":place.optJSONArray("riesgos").toString();
        String action=risks.contains("Garaje")||risks.contains("Sótano")?"No bajes al garaje ni al sótano. "
                     :risks.contains("Planta baja")?"Evita zonas bajas y sube a una planta superior. ":"Evita barrancos, pasos inferiores y zonas inundables. ";
        for(int q=0;gauges!=null&&q<gauges.length();q++){
            String key=gauges.optString(q);
            JSONObject v=rain.get(key);
            if(v==null||!fresh(v)){complete=false;missing.append("Pluviómetro "+key+" sin lectura vigente. ");continue;}
            double h1=val(v,"h1"),h4=val(v,"h4");
            if(!Double.isFinite(h1)){complete=false;missing.append(key+" sin dato 1h. ");continue;}
            int l=(h1>=40||(Double.isFinite(h4)&&h4>=80))?3:h1>=20?2:0;
            level=Math.max(level,l);
            desc.append(key).append(": ").append(h1).append(" mm/1h, ").append(Double.isFinite(h4)?h4:"—").append(" mm/4h. ");
            csv(place.optString("nombre"),"pluvio",key,h1,"mm/1h",String.valueOf(v.opt("fecha")));
        }
        for(int q=0;up!=null&&q<up.length();q++){
            String key=up.optString(q);JSONObject v=rain.get(key);
            if(v!=null&&fresh(v)){
                double h=val(v,"h1");if(Double.isFinite(h)&&h>=20){
                    int l=h>=40?3:2;level=Math.max(level,l);
                    desc.append("AGUAS ARRIBA ").append(key).append(" ").append(h).append(" mm/1h. ");
                }
            }else{complete=false;missing.append("Cabecera "+key+" sin datos. ");}
        }
        for(int q=0;af!=null&&q<af.length();q++){
            String key=af.optString(q);JSONObject v=flows.get(key);
            if(v==null||!fresh(v)){complete=false;missing.append("Aforo "+key+" sin lectura vigente. ");continue;}
            double qv=val(v,"valor"),yellow=val(v,"amarillo"),orange=val(v,"naranja"),red=val(v,"rojo");
            if(!Double.isFinite(qv)){complete=false;missing.append("Aforo "+key+" sin caudal. ");continue;}
            int l=Double.isFinite(red)&&qv>=red?3:Double.isFinite(orange)&&qv>=orange?2:Double.isFinite(yellow)&&qv>=yellow?1:0;
            if(!Double.isFinite(yellow)) {complete=false;missing.append(key+" sin umbrales oficiales. ");}
            level=Math.max(level,l);
            String trend=trend(key,qv,yellow);
            if(trend.contains("subida rápida"))level=Math.max(level,1);
            desc.append(key).append(": ").append(qv).append(" m³/s (").append(trend).append("). ");
            csv(place.optString("nombre"),"aforo",key,qv,"m3/s",String.valueOf(v.opt("fecha")));
        }
        if(aemet.valid){
            if(!aemet.current.isEmpty())desc.append("AVISOS AEMET: ").append(String.join("; ",aemet.current)).append(". ");
            if(!aemet.future.isEmpty())desc.append("PRÓXIMOS: ").append(String.join("; ",aemet.future)).append(". ");
        }
        if(level>0)desc.append(action);
        if(desc.length()==0)desc.append("Sin lecturas confirmadas. ");
        desc.append(missing);
        try{
            out.put("nivel",level);out.put("completo",complete);
            out.put("hora",time());
            out.put("chj","lluvias "+(rs.ok?"recibidas":"SIN DATOS")+" · aforos "+(fs.ok?"recibidos":"SIN DATOS"));
            out.put("aemet",aemet.valid?(aemet.current.isEmpty()?"Sin aviso activo":"Nivel actual "+new String[]{"normal","amarillo","naranja","rojo"}[aemet.active]):"SIN DATOS "+aeError);
            out.put("detalle",desc.toString());
        }catch(Exception ignored){}
        return out;
    }
    String trend(String name,double value,double yellow){
        String key="h_"+name.replaceAll("[^A-Za-z0-9]","_");
        long now=System.currentTimeMillis();
        JSONArray old;
        try{old=new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){old=new JSONArray();}
        JSONArray kept=new JSONArray();Double base=null,near=null;
        for(int i=0;i<old.length();i++){
            JSONObject v=old.optJSONObject(i);if(v==null)continue;
            long t=v.optLong("t",0);
            if(t>now-42*60000L&&t<=now){kept.put(v);if(t<=now-28*60000L)base=v.optDouble("q");}
            if(t>now-12*60000L)near=v.optDouble("q");
        }
        JSONObject current=new JSONObject();
        try{current.put("t",now);current.put("q",value);kept.put(current);}catch(Exception ignored){}
        prefs.edit().putString(key,kept.toString()).apply();
        if(base!=null&&Double.isFinite(yellow)&&value-base>0.25*yellow)return "subida rápida (>25% del amarillo / 30 min)";
        if(near==null)return "sin tendencia";
        double delta=value-near;
        return delta>0.05?"sube":delta<-.05?"baja":"estable";
    }
    void csv(String place,String source,String station,double value,String unit,String observed){
        try{
            String row=csvEscape(time())+","+csvEscape(place)+","+csvEscape(source)+","+csvEscape(station)+","+
                value+","+csvEscape(unit)+","+csvEscape(observed)+"\n";
            File f=new File(getFilesDir(),"lecturas.csv");boolean header=!f.exists();
            try(FileOutputStream out=new FileOutputStream(f,true)){
                if(header)out.write("consulta,lugar,fuente,estacion,valor,unidad,fecha_fuente\n".getBytes(StandardCharsets.UTF_8));
                out.write(row.getBytes(StandardCharsets.UTF_8));
            }
        }catch(Exception e){Log.w("VigilanciaCV","Fallo escribiendo CSV",e);}
    }
    static String csvEscape(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    void alert(int idx,String name,JSONObject result){
        // No se notifica recuperación si hay fuentes desconocidas.
        boolean complete=result.optBoolean("completo",false);
        int now=result.optInt("nivel",0),prev=prefs.getInt("previous_level_"+idx,-1);
        long last=prefs.getLong("last_alert_"+idx,0),t=System.currentTimeMillis();
        if(!complete&&now==0)return;
        if(now==0&&prev<=0)return;
        boolean rising=now>prev;
        if(now>0&&(!rising&&t-last<30*60000L))return;
        if(now==0&&!complete)return;
        String title= name+": "+(now==0?"vuelta a la calma":new String[]{"Normal","Amarillo","Naranja","Rojo"}[now]);
        String body=now==0?"Los sensores configurados muestran normalidad; consulta los avisos oficiales.":result.optString("detalle");
        if(body.length()>550)body=body.substring(0,550);
        int color=now>=3?Color.RED:now==2?0xFFFF8800:now==1?0xFFFFBB33:Color.GREEN;
        Notification.Builder n=new Notification.Builder(this,ALARM).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).setColor(color);
        if(now==3)n.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE);
        try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(100+idx,n.build());}catch(Exception e){Log.w("VigilanciaCV","No se pudo notificar: "+e);}
        prefs.edit().putInt("previous_level_"+idx,now).putLong("last_alert_"+idx,t).apply();
    }
}
