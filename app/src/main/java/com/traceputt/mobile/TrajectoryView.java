package com.traceputt.mobile;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.List;

/** Chart in the image's own pixel coordinate system. Not a calibrated putting path. */
public final class TrajectoryView extends View {
    private List<VideoAnalyzer.Sample> points;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public TrajectoryView(Context c) {super(c);setMinimumHeight(390);}
    public void setPoints(List<VideoAnalyzer.Sample> value) { points=value;invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float width=getWidth(),height=getHeight();
        p.setColor(0xffeef4f6);p.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,width,height,18,18,p);
        p.setColor(0xffd9e4e8);p.setStrokeWidth(1);
        for(int j=1;j<5;j++) {float xx=width*j/5f,yy=height*j/5f;c.drawLine(xx,0,xx,height,p);c.drawLine(0,yy,width,yy,p);}
        if(points==null||points.size()<2) {
            p.setColor(0xff687985);p.setTextSize(27); c.drawText("Sin trayectoria todavía",30,height/2,p);return;
        }
        double minx=Double.MAX_VALUE,miny=Double.MAX_VALUE,maxx=-1e9,maxy=-1e9;
        for(VideoAnalyzer.Sample s:points) {minx=Math.min(minx,s.xPixels);maxx=Math.max(maxx,s.xPixels);miny=Math.min(miny,s.yPixels);maxy=Math.max(maxy,s.yPixels);}
        double dx=Math.max(25,maxx-minx),dy=Math.max(25,maxy-miny);
        double scale=Math.min((width-80)/dx,(height-80)/dy);
        double cx=(minx+maxx)/2d,cy=(miny+maxy)/2d;
        Path path=new Path();int i=0;
        for(VideoAnalyzer.Sample s:points) {
            float px=(float)((s.xPixels-cx)*scale+width/2d);
            float py=(float)((s.yPixels-cy)*scale+height/2d);
            if(i==0 || s.segment!=points.get(i-1).segment)path.moveTo(px,py);else path.lineTo(px,py);
            i++;
        }
        p.setColor(0xff007c89);p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        VideoAnalyzer.Sample first=points.get(0),last=points.get(points.size()-1);
        p.setColor(0xff16a34a);c.drawCircle((float)((first.xPixels-cx)*scale+width/2d),(float)((first.yPixels-cy)*scale+height/2d),10,p);
        p.setColor(0xffd97706);c.drawCircle((float)((last.xPixels-cx)*scale+width/2d),(float)((last.yPixels-cy)*scale+height/2d),10,p);
    }
}