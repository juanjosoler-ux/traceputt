package com.traceputt.mobile;

import java.util.ArrayList;
import java.util.List;

/** Experimental magenta fiducial tracker. Pixel coordinates, never metric ground coordinates. */
public final class MarkerTracker {
    private MarkerTracker() {}
    public static final class Position {
        public final double x, y;
        public final int matchedPixels;
        public final int boundsWidth, boundsHeight;
        public Position(double x, double y, int n, int bw, int bh) {
            this.x=x; this.y=y; matchedPixels=n; boundsWidth=bw; boundsHeight=bh;
        }
    }
    /** Return the largest plausible connected magenta region; no marker if unreliable. */
    public static Position locate(int[] argb, int width, int height) {
        return locate(argb,width,height,null,Double.POSITIVE_INFINITY);
    }
    /** Previous marker helps reject brighter distractions elsewhere in the frame. */
    public static Position locate(int[] argb, int width, int height, Position prior, double maxJumpPx) {
        if (argb==null || width<2 || height<2 || (long)argb.length<(long)width*height) return null;
        final int n=width*height;
        byte[] mask=new byte[n];
        int hits=0;
        for(int i=0;i<n;i++) {
            int v=argb[i], r=(v>>>16)&255, g=(v>>>8)&255, b=v&255;
            if(r>=150 && b>=115 && g<=115 && r-g>=70 && b-g>=65 && Math.abs(r-b)<=125) {
                mask[i]=1; hits++;
            }
        }
        if(hits<16 || hits>n/9) return null;
        int[] queue=new int[hits];
        List<Position> blobs=new ArrayList<>();
        for(int seed=0;seed<n;seed++) {
            if(mask[seed]!=1) continue;
            int head=0,tail=1;queue[0]=seed;mask[seed]=2;
            double sx=0,sy=0;int minX=width,minY=height,maxX=0,maxY=0;
            while(head<tail) {
                int at=queue[head++];int y=at/width,x=at-y*width;
                sx+=x;sy+=y;
                if(x<minX)minX=x;if(x>maxX)maxX=x;
                if(y<minY)minY=y;if(y>maxY)maxY=y;
                if(x>0 && mask[at-1]==1) {mask[at-1]=2;queue[tail++]=at-1;}
                if(x<width-1 && mask[at+1]==1) {mask[at+1]=2;queue[tail++]=at+1;}
                if(y>0 && mask[at-width]==1) {mask[at-width]=2;queue[tail++]=at-width;}
                if(y<height-1 && mask[at+width]==1) {mask[at+width]=2;queue[tail++]=at+width;}
            }
            if(tail<16 || tail>n/10)continue;
            int bw=maxX-minX+1,bh=maxY-minY+1;
            if(bw>width/3 || bh>height/3 || (double)tail/(bw*bh)<.24) continue;
            blobs.add(new Position(sx/tail,sy/tail,tail,bw,bh));
        }
        Position best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(Position p:blobs) {
            double score=p.matchedPixels;
            if(prior!=null) {
                double distance=Math.hypot(p.x-prior.x,p.y-prior.y);
                if(distance>maxJumpPx)continue;
                score-=distance*1.4;
            }
            if(score>bestScore) {bestScore=score;best=p;}
        }
        return best;
    }
}