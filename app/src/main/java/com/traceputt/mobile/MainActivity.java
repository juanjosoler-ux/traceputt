package com.traceputt.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** First Android capture-analysis experiment; no fitting claims. */
public class MainActivity extends Activity {
    private static final int PICK_VIDEO=101;
    private static final int RECORD_VIDEO=102;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri videoUri;
    private TextView fileInfo,status;
    private Button analyze,share;
    private TrajectoryView plot;
    private VideoAnalyzer.Result lastResult;
    private long analysisStartMs=0L;
    private Button windowButton;
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(radius));return gd;}

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff052b36);
        getWindow().setNavigationBarColor(0xfff4f8fa);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff4f8fa);
        LinearLayout main=new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20),dp(24),dp(20),dp(36));
        scroll.addView(main);
        setContentView(scroll);

        TextView header=text("TRACEPUTT",32,0xff073b46,true);
        header.setLetterSpacing(0.13f);main.addView(header);
        TextView title=text("Lab  /  Análisis experimental",16,0xff48727c,false);
        title.setPadding(0,dp(3),0,dp(18));main.addView(title);
        TextView hint=text("Analiza un vídeo de putting con un marcador magenta en el putter. Todo se procesa en tu móvil, sin enviar vídeos a servidores.",15,0xff334d56,false);
        hint.setPadding(0,0,0,dp(24));main.addView(hint);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(18),dp(18),dp(20));card.setBackground(bg(Color.WHITE,16));
        main.addView(card,new LinearLayout.LayoutParams(-1,-2));
        card.addView(text("01  VÍDEO DE PRUEBA",12,0xff137a81,true));
        fileInfo=text("No hay vídeo seleccionado",14,0xff455a64,false);
        fileInfo.setPadding(0,dp(14),0,dp(10));card.addView(fileInfo);
        Button select=button("Seleccionar vídeo MP4",0xff073b46,Color.WHITE);
        card.addView(select,new LinearLayout.LayoutParams(-1,dp(51)));
        select.setOnClickListener(v->selectVideo());
        Button record=button("Grabar vídeo con la cámara",0xffe3eeee,0xff073b46);
        LinearLayout.LayoutParams recordLp=new LinearLayout.LayoutParams(-1,dp(51));recordLp.topMargin=dp(10);
        card.addView(record,recordLp);
        record.setOnClickListener(v->recordVideo());
        analyze=button("Analizar marcador",0xff0b938b,Color.WHITE);
        LinearLayout.LayoutParams analyzeLp=new LinearLayout.LayoutParams(-1,dp(51));analyzeLp.topMargin=dp(10);
        card.addView(analyze,analyzeLp);analyze.setEnabled(false);
        windowButton=button("Ventana: 0–8 segundos",0xffe3eeee,0xff073b46);
        LinearLayout.LayoutParams winLp=new LinearLayout.LayoutParams(-1,dp(45));winLp.topMargin=dp(10);
        card.addView(windowButton,winLp);
        windowButton.setOnClickListener(v->{
            analysisStartMs=analysisStartMs==0?5000:analysisStartMs==5000?10000:analysisStartMs==10000?20000:0;
            windowButton.setText("Ventana: "+(analysisStartMs/1000)+"–"+(analysisStartMs/1000+8)+" segundos");
            status.setText("La ventana debe coincidir con el momento del putt.");
        });
        analyze.setOnClickListener(v->startAnalysis());
        status=text("Esperando vídeo de prueba.",14,0xff425c64,false);
        status.setPadding(0,dp(16),0,0);card.addView(status);

        TextView caption=text("02  TRAYECTORIA APARENTE",12,0xff137a81,true);
        caption.setPadding(0,dp(24),0,dp(10));main.addView(caption);
        plot=new TrajectoryView(this);
        main.addView(plot,new LinearLayout.LayoutParams(-1,dp(240)));
        TextView legend=text("● Verde: inicio    ● Naranja: final\nCoordenadas del vídeo (px). No es el plano real del suelo.",12,0xff627782,false);
        legend.setPadding(0,dp(9),0,dp(19));main.addView(legend);
        share=button("Compartir resultado JSON",0xffdce9ec,0xff0d414b);
        main.addView(share,new LinearLayout.LayoutParams(-1,dp(50)));
        share.setEnabled(false);share.setOnClickListener(v->shareJson());
        TextView caution=text("VERSIÓN ALFA · Seguimiento óptico de marcador magenta, no fitting. NO mide el instante de impacto, el ángulo de cara, ni distancias calibradas. No utilizar para elegir un putter.",12,0xff91611a,true);
        caution.setLineSpacing(dp(3),1f);
        caution.setPadding(dp(15),dp(15),dp(15),dp(15));
        caution.setBackground(bg(0xfffff3df,12));
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(-1,-2);c.topMargin=dp(22);main.addView(caution,c);
    }
    private TextView text(String t,int size,int color,boolean bold) {
        TextView x=new TextView(this);x.setText(t);x.setTextColor(color);x.setTextSize(size);
        x.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return x;
    }
    private Button button(String label,int color,int foreground) {
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(foreground);b.setBackground(bg(color,12));
        b.setGravity(Gravity.CENTER);return b;
    }
    private void selectVideo() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,PICK_VIDEO);
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if((req==PICK_VIDEO || req==RECORD_VIDEO) && result==RESULT_OK && data!=null && data.getData()!=null) {
            videoUri=data.getData();fileInfo.setText("Vídeo seleccionado · listo para analizar");
            status.setText("Usa un marcador magenta visible en todos los fotogramas.");
            analyze.setEnabled(true); share.setEnabled(false);lastResult=null;plot.setPoints(null);
        } else if(req==RECORD_VIDEO && result==RESULT_OK) {
            status.setText("La cámara no ha devuelto un vídeo. Guárdalo y usa Seleccionar vídeo.");
        }
    }
    private void recordVideo() {
        Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        i.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 12);
        if (i.resolveActivity(getPackageManager()) == null) {
            status.setText("No hay aplicación de cámara disponible; usa Seleccionar vídeo.");
            return;
        }
        startActivityForResult(i, RECORD_VIDEO);
    }
    private void startAnalysis() {
        if(videoUri==null)return;
        final Uri selected=videoUri;
        final long start=analysisStartMs;
        analyze.setEnabled(false);share.setEnabled(false);
        status.setText("Procesando vídeo localmente (hasta 8 segundos)…");
        worker.execute(()->{
            VideoAnalyzer.Result result=VideoAnalyzer.analyze(getApplicationContext(),selected,start, fraction -> {
                runOnUiThread(()->{if(!isFinishing() && !isDestroyed())
                    status.setText("Analizando vídeo… " + (int)(fraction*100) + "%");});
            });
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                lastResult=result;
                analyze.setEnabled(true);
                status.setText(result.summary());
                if(result.error==null) {plot.setPoints(result.points);share.setEnabled(true);}
                else plot.setPoints(null);
            });
        });
    }
    private void shareJson() {
        if(lastResult==null||lastResult.error!=null)return;
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TEXT,lastResult.toJson());
        startActivity(Intent.createChooser(i,"Compartir resultados de TracePutt"));
    }
    @Override protected void onDestroy() {worker.shutdownNow();super.onDestroy();}
}
