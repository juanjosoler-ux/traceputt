package es.vigilanciacv.personal;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private android.content.SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int NAVY = Color.rgb(15,27,47), PALE = Color.rgb(242,246,250);
    private final int[] C = {Color.rgb(34,132,93), Color.rgb(204,151,18), Color.rgb(220,115,27), Color.rgb(194,49,46)};
    private static final String NOTE = "Herramienta personal sin garantía. Mandan los avisos oficiales: AEMET, 112 Comunitat Valenciana y ES-Alert. Ante la duda: no bajes a garajes ni sótanos, no cruces barrancos ni pasos inferiores, sube a una planta alta.";
    private static final int EXPORT_CSV = 101;
    private final Runnable refresh = new Runnable() { @Override public void run() { render(); handler.postDelayed(this, 60000); } };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("vigilancia", MODE_PRIVATE);
        if (!prefs.contains("places")) {
            JSONArray a = new JSONArray();
            a.put(place("Casa", "València, Comunitat Valenciana", 39.4699, -0.3763, "Litoral norte de Valencia", "774602"));
            a.put(place("Apartamento", "Benicàssim, Castellón", 40.0555, 0.0656, "Litoral sur de Castellón", "771204"));
            prefs.edit().putString("places", a.toString()).apply();
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != getPackageManager().PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
        }
        render(); handler.postDelayed(refresh, 60000);
    }
    static JSONObject place(String name, String address, double lat, double lon, String zone, String code) {
        JSONObject j = new JSONObject();
        try { j.put("nombre",name); j.put("direccion",address); j.put("lat",lat); j.put("lon",lon);
              j.put("zona",zone); j.put("codigo",code); j.put("riesgos",new JSONArray());
              j.put("pluviometros",new JSONArray()); j.put("aforos",new JSONArray()); j.put("cabecera",new JSONArray());
              j.put("ubicacion_aproximada",true); j.put("costa",true);
        } catch(Exception ignored) {} return j;
    }
    JSONArray places() { try {return new JSONArray(prefs.getString("places","[]"));} catch(Exception e){return new JSONArray();} }
    void persist(JSONArray a) {prefs.edit().putString("places",a.toString()).apply();render();}
    TextView label(String text, int sp, int color, boolean strong) {
        TextView v = new TextView(this); v.setText(text); v.setTextColor(color); v.setTextSize(sp);
        if (strong) v.setTypeface(null,1);
        v.setPadding(0,4,0,4);
        return v;
    }
    LinearLayout column() { LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    Button button(String text, Runnable action) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;
    }
    void addButton(LinearLayout where,String text,Runnable action){where.addView(button(text,action));}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void render() {
        if (isFinishing()) return;
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(PALE);
        root=column();root.setPadding(dp(18),dp(18),dp(18),dp(24));sc.addView(root);setContentView(sc);
        root.addView(label("VIGILANCIA CV",26,NAVY,true));
        root.addView(label("Lluvias · Barrancos · AEMET",15,Color.DKGRAY,false));
        boolean run=prefs.getBoolean("monitoring",false);
        root.addView(label(run?"● Vigilancia solicitada (cada 5 minutos)":"● Vigilancia detenida",14,run?C[0]:C[2],true));
        root.addView(label("Último intento: "+prefs.getString("last_check","aún no realizado"),12,Color.GRAY,false));
        addButton(root,"▶ ACTIVAR VIGILANCIA",()->send(VigilanceService.START));
        addButton(root,"↻ Comprobar ahora (respetando intervalo mínimo)",()->send(VigilanceService.CHECK));
        addButton(root,"■ Detener vigilancia",()->send(VigilanceService.STOP));
        JSONArray a=places();
        for(int i=0;i<a.length();i++){
            final int ix=i;
            JSONObject place=a.optJSONObject(i); if(place==null)continue;
            JSONObject result = new JSONObject();
            try { result=new JSONObject(prefs.getString("result_"+i,"{}"));}catch(Exception ignored){}
            final int level=result.optInt("nivel",-1);
            boolean complete=result.optBoolean("completo",false);
            LinearLayout card=column();
            card.setPadding(dp(15),dp(12),dp(15),dp(14));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);bg.setCornerRadius(dp(16));bg.setStroke(dp(2),level<0||!complete?Color.GRAY:C[Math.min(3,level)]);
            card.setBackground(bg); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(14),0,dp(2));root.addView(card,cp);
            String name=place.optString("nombre","Lugar "+(i+1));
            card.addView(label(name.toUpperCase(),19,NAVY,true));
            String levelName=level<0||!complete?"SIN DATOS COMPLETOS":new String[]{"NORMAL","AMARILLO","NARANJA","ROJO"}[Math.min(3,level)];
            card.addView(label("● "+levelName,18,level<0||!complete?Color.DKGRAY:C[Math.min(3,Math.max(0,level))],true));
            card.addView(label(place.optString("direccion",""),13,Color.DKGRAY,false));
            card.addView(label(place.optString("zona",""),12,Color.GRAY,false));
            if(place.optBoolean("ubicacion_aproximada",true))
                card.addView(label("Ubicación aproximada del municipio: confirma una dirección exacta.",12,C[2],true));
            card.addView(label("SAIH Júcar: "+result.optString("chj","Sin lectura"),13,NAVY,false));
            card.addView(label("AEMET: "+result.optString("aemet","Sin lectura"),13,NAVY,false));
            card.addView(label(result.optString("detalle","Datos no disponibles. Configura estaciones y activa la vigilancia."),13,Color.DKGRAY,false));
            card.addView(label("Actualizado: "+result.optString("hora","sin datos"),12,Color.GRAY,false));
            addButton(card,"⚙ Configurar ubicación y riesgos",()->configure(ix));
            addButton(card,"☔ Seleccionar pluviómetros",()->pick(ix,"pluviometros","catalog_rains"));
            addButton(card,"〰 Seleccionar aforos",()->pick(ix,"aforos","catalog_flows"));
            addButton(card,"🌧 Seleccionar estaciones aguas arriba",()->pick(ix,"cabecera","catalog_rains"));
        }
        addButton(root,"📋 Exportar lecturas CSV",this::exportCSV);
        addButton(root,"📡 Abrir radar oficial de AEMET",()->open("https://www.aemet.es/es/eltiempo/observacion/radar"));
        addButton(root,"Fuente: SAIH Júcar",()->open("https://saih.chj.es/mapa-lluvias"));
        addButton(root,"Fuente: avisos AEMET",()->open("https://www.aemet.es/es/eltiempo/prediccion/avisos"));
        root.addView(label(NOTE,14,Color.rgb(120,33,33),true));
        root.addView(label("Esta versión requiere comprobar los campos de CHJ en tu móvil antes de confiar en alertas hidrológicas. La vigilancia en segundo plano puede ser suspendida por Android.",12,Color.DKGRAY,false));
    }
    void open(String url){ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    void send(String action){
        try{
            Intent i=new Intent(this,VigilanceService.class); i.setAction(action);
            if (action.equals(VigilanceService.STOP)) stopService(i);
            else if(Build.VERSION.SDK_INT>=26) startForegroundService(i);else startService(i);
            handler.postDelayed(this::render,500);
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("No se pudo iniciar: "+e.getMessage()).setPositiveButton("Aceptar",null).show();}
    }
    void configure(int idx) {
        JSONArray arr=places();JSONObject p=arr.optJSONObject(idx);if(p==null)return;
        EditText address=new EditText(this);address.setSingleLine(false);address.setText(p.optString("direccion"));address.setHint("Pueblo, barrio o dirección");
        LinearLayout form=column();form.setPadding(dp(16),0,dp(16),0);form.addView(label("Localización del lugar",16,NAVY,true));form.addView(address);
        String[] options={"Garaje","Sótano","Planta baja","Cerca de barranco"};
        JSONArray risk=p.optJSONArray("riesgos");boolean[] checks=new boolean[4];
        for(int k=0;k<4;k++) for(int z=0;risk!=null&&z<risk.length();z++) if(options[k].equals(risk.optString(z)))checks[k]=true;
        new AlertDialog.Builder(this).setTitle(p.optString("nombre"))
            .setView(form).setMultiChoiceItems(options,checks,(dialog,which,checked)->checks[which]=checked)
            .setPositiveButton("Buscar dirección y confirmar",(dialog,which)-> {
                String query=address.getText().toString().trim();if(query.isEmpty())return;
                JSONArray chosen=new JSONArray();for(int k=0;k<4;k++)if(checks[k])chosen.put(options[k]);
                new Thread(()->{
                    try {
                        String url="https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=es&q="+URLEncoder.encode(query,"UTF-8");
                        String raw=VigilanceService.http(url,18);
                        JSONArray res=new JSONArray(raw);
                        if(res.length()==0)throw new Exception("No hay resultados");
                        JSONObject found=res.getJSONObject(0);
                        double lat=Double.parseDouble(found.getString("lat")),lon=Double.parseDouble(found.getString("lon"));
                        String display=found.optString("display_name",query);
                        handler.post(()->new AlertDialog.Builder(this)
                          .setTitle("Confirmar localización")
                          .setMessage(display+"\n"+lat+", "+lon+"\n\nLa zona AEMET debe confirmarse si cambias de municipio.")
                          .setPositiveButton("Confirmar",(d,w)->{
                              try{p.put("direccion",query);p.put("lat",lat);p.put("lon",lon);p.put("riesgos",chosen);p.put("ubicacion_aproximada",false);
                                  arr.put(idx,p);persist(arr);}catch(Exception ignored){}
                          }).setNegativeButton("Cancelar",null).show());
                    }catch(Exception e){handler.post(()->new AlertDialog.Builder(this).setMessage("No se pudo geocodificar: "+e.getMessage()).setPositiveButton("Aceptar",null).show());}
                }).start();
            })
            .setNegativeButton("Cancelar",null).show();
    }
    void pick(int idx,String key,String source){
        JSONArray places=places();JSONObject p=places.optJSONObject(idx);
        if(p==null)return;
        JSONArray catalog;
        try{catalog=new JSONArray(prefs.getString(source,"[]"));}catch(Exception e){catalog=new JSONArray();}
        if(catalog.length()==0){new AlertDialog.Builder(this).setTitle("Todavía sin catálogo").setMessage("Activa una comprobación. Si la CHJ no entrega datos compatibles, no se ofrecerán estaciones inventadas.").setPositiveButton("Aceptar",null).show();return;}
        ArrayList<JSONObject> list=new ArrayList<>();
        for(int i=0;i<catalog.length();i++){JSONObject ob=catalog.optJSONObject(i);if(ob!=null)list.add(ob);}
        final double la=p.optDouble("lat",0),lo=p.optDouble("lon",0);
        list.sort((a,b)->Double.compare(distance(la,lo,a.optDouble("lat",999),a.optDouble("lon",999)),distance(la,lo,b.optDouble("lat",999),b.optDouble("lon",999))));
        if(list.size()>60)list=new ArrayList<>(list.subList(0,60));
        final ArrayList<JSONObject> finalList=list;
        JSONArray chosen=p.optJSONArray(key);HashSet<String> selected=new HashSet<>();
        for(int i=0;chosen!=null&&i<chosen.length();i++)selected.add(chosen.optString(i));
        String[] options=new String[list.size()];boolean[] checks=new boolean[list.size()];
        for(int i=0;i<list.size();i++){JSONObject ob=list.get(i);String name=ob.optString("nombre");checks[i]=selected.contains(name);
            double km=distance(la,lo,ob.optDouble("lat",999),ob.optDouble("lon",999));
            options[i]=name+(km>10000?"":" · "+String.format(Locale.ROOT,"%.1f km",km));
        }
        new AlertDialog.Builder(this).setTitle(key.equals("cabecera")?"Solo estaciones REALMENTE aguas arriba":key.equals("aforos")?"Aforos (no basta con proximidad)":"Pluviómetros próximos")
            .setMultiChoiceItems(options,checks,(d,k,b)->checks[k]=b)
            .setPositiveButton("Guardar",(d,w)->{
                JSONArray out=new JSONArray();for(int i=0;i<checks.length;i++)if(checks[i])out.put(finalList.get(i).optString("nombre"));
                try{p.put(key,out);places.put(idx,p);persist(places);}catch(Exception ignored){}
            }).setNegativeButton("Cancelar",null).show();
    }
    static double distance(double lat1,double lon1,double lat2,double lon2){
        double r1=Math.toRadians(lat1),r2=Math.toRadians(lat2),delta=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
        double h=Math.pow(Math.sin(delta/2),2)+Math.cos(r1)*Math.cos(r2)*Math.pow(Math.sin(dl/2),2);
        return 12742*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    void exportCSV(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"vigilancia_cv_lecturas.csv");startActivityForResult(i,EXPORT_CSV);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT_CSV&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(InputStream in=openFileInput("lecturas.csv");OutputStream out=getContentResolver().openOutputStream(data.getData())){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                Toast.makeText(this,"CSV exportado",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Todavía no hay lecturas: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);super.onDestroy();}
}
