package com.traceputt.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline, experimental visual tracker. Does not identify impact or perform fitting. */
public final class VideoAnalyzer {
    public interface ProgressListener { void onProgress(double fraction); }
    public static final class Sample {
        public final double timeSeconds, xPixels, yPixels;
        public final int markerArea, segment;
        Sample(double t, double x, double y, int a, int seg) {
            timeSeconds=t;xPixels=x;yPixels=y;markerArea=a;segment=seg;
        }
    }
    public static final class Result {
        public final List<Sample> points = new ArrayList<>();
        public int framesAttempted, segments, missedFrames;
        public long originalDurationMs, analyzedStartMs, analyzedStopMs;
        public double trackedPathPixels, coverage;
        public String error;
        public String summary() {
            if(error!=null) return error;
            return String.format(Locale.US,
                "Ventana: %.1f–%.1f s\nMarcador detectado: %d/%d fotogramas (%.0f%%)\nTramos: %d · Recorrido aparente: %.1f px\n%s\nNo mide impacto, cara ni milímetros.",
                analyzedStartMs/1000.0,analyzedStopMs/1000.0,points.size(),framesAttempted,coverage,segments,trackedPathPixels,coverage<65?"Calidad baja: revisa luz y marcador.":"Seguimiento experimental disponible.");
        }
        public String toJson() {
            StringBuilder sb=new StringBuilder();
            sb.append("{\"product\":\"TracePutt Lab\",\"version\":\"0.4-alpha\",\"units\":\"image_pixels\",")
              .append("\"impactDetected\":false,\"faceAngleMeasured\":false,\"calibrated\":false,\"duration_ms\":")
              .append(originalDurationMs).append(",\"start_ms\":").append(analyzedStartMs).append(",\"stop_ms\":").append(analyzedStopMs).append(",\"sampledFrames\":").append(framesAttempted)
              .append(",\"coverage_pct\":").append(String.format(Locale.US,"%.1f",coverage))
              .append(",\"segments\":").append(segments)
              .append(",\"trackedPathPixels\":").append(String.format(Locale.US,"%.2f",trackedPathPixels))
              .append(",\"points\":[");
            for(int i=0;i<points.size();i++) {
                Sample s=points.get(i);if(i>0)sb.append(',');
                sb.append(String.format(Locale.US,"{\"t\":%.4f,\"x\":%.2f,\"y\":%.2f,\"area_px\":%d,\"segment\":%d}",
                    s.timeSeconds,s.xPixels,s.yPixels,s.markerArea,s.segment));
            }
            return sb.append("]}").toString();
        }
    }
    private VideoAnalyzer() {}
    public static Result analyze(Context context, Uri uri) {return analyze(context,uri,0L,null);}
    public static Result analyze(Context context, Uri uri, ProgressListener callback) {return analyze(context,uri,0L,callback);}
    public static Result analyze(Context context, Uri uri, long startMs, ProgressListener callback) {
        Result out=new Result();
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context,uri);
            String duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if(duration==null){out.error="El vídeo no tiene duración legible.";return out;}
            out.originalDurationMs=Long.parseLong(duration);
            if(startMs<0 || startMs>=out.originalDurationMs-300){out.error="La ventana elegida empieza después de la duración útil del vídeo.";return out;}
            final long stopMs=Math.min(out.originalDurationMs,startMs+8000L);
            out.analyzedStartMs=startMs;out.analyzedStopMs=stopMs;
            if(stopMs-startMs<300){out.error="El vídeo es demasiado corto.";return out;}
            int sourceW=parsePositive(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),640);
            int sourceH=parsePositive(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),480);
            int w=Math.min(400,sourceW);
            int h=Math.max(1,(int)Math.round(sourceH*(w/(double)sourceW)));
            MarkerTracker.Position previous=null;
            int missed=0,segment=0;
            for(long t=startMs;t<stopMs;t+=100) {
                if(Thread.currentThread().isInterrupted()){out.error="Análisis cancelado.";return out;}
                out.framesAttempted++;
                Bitmap frame=null;
                try {
                    if(Build.VERSION.SDK_INT>=27) {
                        frame=retriever.getScaledFrameAtTime(t*1000L,MediaMetadataRetriever.OPTION_CLOSEST,w,h);
                    } else {
                        Bitmap raw=retriever.getFrameAtTime(t*1000L,MediaMetadataRetriever.OPTION_CLOSEST);
                        if(raw!=null) {
                            frame=Bitmap.createScaledBitmap(raw,w,h,false);
                            if(frame!=raw)raw.recycle();
                        }
                    }
                    if(frame==null){out.missedFrames++;missed++;if(missed>=3)previous=null;continue;}
                    int fw=frame.getWidth(),fh=frame.getHeight();
                    int[] argb=new int[fw*fh];
                    frame.getPixels(argb,0,fw,0,0,fw,fh);
                    double maxJump=Math.max(18,fw*.18);
                    MarkerTracker.Position match=MarkerTracker.locate(argb,fw,fh,previous,maxJump);
                    if(match==null){
                        missed++;out.missedFrames++;
                        if(missed>=3)previous=null;
                    } else {
                        if(previous==null){segment++;out.segments=segment;}
                        if(previous!=null && !out.points.isEmpty())
                            out.trackedPathPixels+=Math.hypot(match.x-previous.x,match.y-previous.y);
                        out.points.add(new Sample(t/1000d,match.x,match.y,match.matchedPixels,segment));
                        previous=match;missed=0;
                    }
                } finally {if(frame!=null)frame.recycle();}
                if(callback!=null && out.framesAttempted%5==0)callback.onProgress(Math.min(1.0,(t-startMs+100.0)/(stopMs-startMs)));
            }
            out.coverage=100d*out.points.size()/Math.max(1,out.framesAttempted);
            if(out.points.size()<8)out.error="No hay suficientes puntos fiables. Revisa el marcador magenta, la iluminación y la estabilidad de la cámara.";
            if(callback!=null)callback.onProgress(1.0);
        } catch(Exception e) {
            out.error="No se pudo procesar el vídeo ("+e.getClass().getSimpleName()+").";
        } finally {
            try{retriever.release();}catch(Exception ignored){}
        }
        return out;
    }
    private static int parsePositive(String text,int fallback) {
        try {int value=Integer.parseInt(text);return value>0?value:fallback;}
        catch(Exception e){return fallback;}
    }
}