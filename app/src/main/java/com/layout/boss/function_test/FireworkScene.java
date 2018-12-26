package com.layout.boss.function_test;
import java.util.LinkedList;
import java.util.Random;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

enum DucFireworkSceneAnimateState {
    //Firework state
    asReady, asRunning, asPause
}

enum DucAnimationState{
    Idling, Moving, Exploding, Done
}

//IDEA:
    /*
    We represent the firework as a circle
        Each firework will be fired from a starting point until it reaches the given destination
            (which is usually randomly generated)
        The firework will move and gradually slow down using the easing equation
        Once it reaches the destination, the firework explodes into the given number of fragments
        Each fragment will be shot out randomly from the core of the explosion and gradually fade out
            using the easing equation

        For easing: http://www.gizma.com/easing/
     */

class Misc {
    public static boolean isDebugging = false;
    public static int backgroundColor = Color.BLACK;
    public static int screenHeight;
    public static int screenWidth;

    //Firework parameters
    //Timing
    public static long minDuration = 2000;
    public static long maxDuration = 3000;
    public static int minFragment = 40;
    public static int maxFragment = 60;
    public static float minfireworkRadius = 7;
    public static float maxFireworkRadius = 12.0f;    //Radius of the firework in pixel
    //Coordination
        //Min and max possible spawn and target positions
    public static int minX;
    public static int minY;
    public static int maxX;
    public static int maxY;

    public static void CalculateBoundary(int width, int height){
        screenHeight = height;
        screenWidth = width;

        minX = (int) (screenWidth * 0.3f);
        minY = (int) (screenHeight * 0.2f);
        maxX = (int) (screenWidth * 0.7f);
        maxY = (int) (screenHeight * 0.8f);
    }
    //Check whether 2 coord are near each other within acceptable range
    public static boolean Near(int parm1, int parm2, int epsilon){
        return Math.abs(parm1 - parm2) <= epsilon ? true : false;
    }
    /**
     * The basic function for easing.
     * @param t is the current time (or position) of the tween.
     *          This can be seconds or frames, steps, seconds, ms, whatever –
     *          as long as the unit is the same as is used for the total time [3].
     * @param b is the beginning value
     * @param c is the change between the beginning and destination value of the property.
     * @param d is the total time of the tween.
     * @return the eased value
     */
    static public double EaseOutCirc(float t, float b, float c, float d) {
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return c * Math.sqrt(1 - t*t) + b;
    }
    static public double EaseOutQuart(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return -c * (t*t*t*t - 1) + b;
    }
    static public double EaseOutCubic(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return c*(t*t*t + 1) + b;
    }
    static public double LinearTween(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        return c*t/d + b;
    }
}

class Firework{

    //Basic info
    private long fireworkDuration = 2000;    //How long does it take for the firework to get to the destination? In millisecond
    private float radius = 5;
    private int minExplodeRadius = 7;           //Minimum explosion radius
    private int maxExplodeRadius = 150;         //Maximum explosion radius
    private int color;
    public DucAnimationState state = DucAnimationState.Idling;
    private Random random;

    private int startX = 0;
    private int startY = 0;
    private int endX = 0;
    private int endY = 0;
    private long startTime;

    //Fragment info
    private Fragment[] fragmentList;
    private int numberOfFragment;

    Firework(){
        random = new Random();
        fragmentList = new Fragment[Misc.maxFragment];
        for(int indx = 0; indx < fragmentList.length; indx++){
            fragmentList[indx] = new Fragment();
        }
    }

    public void StartFirework(long fireworkDuration, float radius, int endX, int endY){
        this.fireworkDuration = fireworkDuration;

        this.startX = random.nextInt(Misc.maxX - (Misc.minX == 0 ? -1 : Misc.minX)) + Misc.minX;
        this.startY = Misc.screenHeight;
        this.endX = endX == 0 ? startX : endX;
        this.endY = endY;
        this.startTime = System.currentTimeMillis();
        this.radius = radius;

        int red = ( int )( random.nextFloat() * 128 ) + 128;
        int blue = ( int )( random.nextFloat() * 128 ) + 128;
        int green = ( int )( random.nextFloat() * 128 ) + 128;

        //Alpha is always at max
        color = Color.rgb(red, green, blue);

        //FOR DEBUGGING PURPOSE
        //Coordination
        if (Misc.isDebugging == true){
            startX = 500;
            startY = 0;
            color = Color.WHITE;
            this.radius = 5;
        }
        state = DucAnimationState.Moving;
    }
    public void Explode(int centerX, int centerY){
        numberOfFragment = random.nextInt(Misc.maxFragment == Misc.minFragment ? 1 : Misc.maxFragment - Misc.minFragment) + Misc.minFragment;
        state = DucAnimationState.Exploding;

        for(int indx = 0; indx < numberOfFragment; indx++){
            int endX = centerX + (random.nextInt(2) % 2 * 2 - 1) * //either 1 or -1
                    (random.nextInt(maxExplodeRadius - minExplodeRadius) + minExplodeRadius);
            int endY = centerY + (random.nextInt(2) % 2 * 2 - 1) * //either 1 or -1
                    (random.nextInt(maxExplodeRadius - minExplodeRadius) + minExplodeRadius);
            long duration = (long) ((random.nextLong() % (Misc.maxFragment - Misc.minDuration) + Misc.minDuration) * 0.8f);
            float radius = (random.nextFloat() % (Misc.maxFireworkRadius - minExplodeRadius) + Misc.minfireworkRadius) * 0.5f;
            fragmentList[indx].StartFragment(centerX, centerY, endX, endY, radius, duration);
        }
    }

    //Fragment thingy
    private boolean CheckFreeFragments(){
        for(int indx = 0; indx < fragmentList.length; indx++){
            if (fragmentList[indx].state != DucAnimationState.Done){
                return false;
            }
        }
        return true;
    }

    public void doDraw(Canvas canvas, Paint paint){
        switch(state){
            case Moving:
                //Simple, draw a line until we reach destination :D
                paint.setColor(color);

                long curTime = System.currentTimeMillis() - startTime;
                float nextX = (float) Misc.EaseOutCubic(curTime, startX, endX - startX, fireworkDuration);
                float nextY = (float) Misc.EaseOutCubic(curTime, startY, endY - startY, fireworkDuration);

                if ((Misc.Near((int) nextX, endX, 20) == true && Misc.Near((int) nextY, endY, 20) == true) || curTime > fireworkDuration){
                    paint.setColor(Misc.backgroundColor);
                    Explode((int) nextX, (int) nextY);
                }

                canvas.drawCircle(nextX, nextY, radius, paint);
                break;
            case Exploding:
                for (int indx = 0; indx < numberOfFragment; indx++) {
                    fragmentList[indx].doDraw(canvas, paint);
                }

                if (CheckFreeFragments() == true){
                    //All fragment fxinish moving
                    state = DucAnimationState.Done;
                    numberOfFragment = 0;
                }
                break;
        }
    }
}

class Fragment{
    //Basic info
    public DucAnimationState state = DucAnimationState.Done;
    private int startX = 0;
    private int startY = 0;
    private int endX = 0;
    private int endY = 0;

    private float radius = 2;
    private long duration = 0;
    private long startTime = 0;

    private int color;
    private int red;
    private int green;
    private int blue;

    private Random random;

    public Fragment(){
        random = new Random();
    }

    public void StartFragment(int startX, int startY, int endX, int endY, float radius, long duration){
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;

        this.radius = radius;
        this.duration = duration;

        this.startTime = System.currentTimeMillis();

        red = ( int )( random.nextFloat() * 128 ) + 128;
        blue = ( int )( random.nextFloat() * 128 ) + 128;
        green = ( int )( random.nextFloat() * 128 ) + 128;

        //Alpha is always at max
        color = Color.rgb(red, green, blue);

        state = DucAnimationState.Moving;
    }
    public void doDraw(Canvas canvas, Paint paint){
        switch(state) {
            case Moving:
                //Simple, draw a circle until we reach destination :D
                long curTime = System.currentTimeMillis() - startTime;
                float nextAlpha = (float) Misc.LinearTween(curTime, 255, -255, duration);
                paint.setColor(color);
                paint.setAlpha((int) nextAlpha);

                float nextX = (float) Misc.EaseOutCubic(curTime, startX, endX - startX, duration);
                float nextY = (float) Misc.EaseOutCubic(curTime, startY, endY - startY, duration);

                if (((nextX == endX && nextY == endY) || curTime > duration) && nextAlpha <= 0) {
                    state = DucAnimationState.Done;
                }

                canvas.drawCircle(nextX, nextY, radius, paint);
                break;
        }
    }
}

public class FireworkScene extends SurfaceView implements  SurfaceHolder.Callback {
    class GameThread extends Thread
    {
        private boolean mRun = false;

        private SurfaceHolder surfaceHolder;
        private DucFireworkSceneAnimateState state;
        private Context context;
        private Handler handler;
        private Paint paint;

        private int maxNumberOfFirework = 20;
        public LinkedList<Firework> freeFireworkList;  //List of free fireworks
        public LinkedList<Firework> shotFireworkList;  //List of shot fireworks
        private long timer;                             //We shoot firework on periodically
        private long fireworkInterval = 1500;

        private Random random;

        GameThread( SurfaceHolder surfaceHolder, Context context, Handler handler )
        {
            random = new Random();

            this.surfaceHolder = surfaceHolder;
            this.context = context;
            this.handler = handler;

            Misc.CalculateBoundary(getWidth(), getHeight());

            //Initialize
            freeFireworkList = new LinkedList<>();
            shotFireworkList = new LinkedList<>();
            for(int indx = 0; indx < maxNumberOfFirework; indx++){
                //Add a bunch of firework
                freeFireworkList.add(new Firework());
            }
            timer = System.currentTimeMillis();

            paint = new Paint();
            paint.setStrokeWidth( 2 / getResources().getDisplayMetrics().density );
            //paint.setColor( Color.TRANSPARENT );
            paint.setColor(Color.BLACK);
            paint.setAntiAlias( true );
        }

        private void StartFirework(Firework firework){
            //Randomize stuff
            long duration = Math.abs(random.nextLong() % (Misc.maxDuration - Misc.minDuration)) + Misc.minDuration;
            float radius = random.nextFloat() % (Misc.maxFireworkRadius - Misc.minfireworkRadius) + Misc.minfireworkRadius;
            //Since at first, when the view has not been fully initialized, all the max and min width and height are 0
            //  therefore, we need to + 1 to make the range [0, bound) works ( [0, 0) will break the program!)
            int targetX = random.nextInt(Misc.maxX - (Misc.minX == 0 ? -1 : Misc.minX)) + Misc.minX;
            int targetY = random.nextInt(Misc.maxY - (Misc.minY == 0 ? -1 : Misc.minY)) + Misc.minY;

            firework.StartFirework(duration, radius, targetX, targetY);
        }

        //Overwrite stuff
        public void doStart()
        {
            synchronized ( surfaceHolder )
            {
                setState( DucFireworkSceneAnimateState.asRunning );
            }
        }

        public void pause()
        {
            synchronized ( surfaceHolder )
            {
                if ( state == DucFireworkSceneAnimateState.asRunning )
                    setState( DucFireworkSceneAnimateState.asPause );
            }
        }
        public void unpause()
        {
            setState( DucFireworkSceneAnimateState.asRunning );
        }
        @Override
        public void run()
        {
            while ( mRun )
            {
                Canvas c = null;
                try
                {
                    c = surfaceHolder.lockCanvas( null );

                    synchronized ( surfaceHolder )
                    {
                        if ( state == DucFireworkSceneAnimateState.asRunning )
                            doDraw( c );
                    }
                }
                finally
                {
                    if ( c != null )
                    {
                        surfaceHolder.unlockCanvasAndPost( c );
                    }
                }
            }
        }

        public void setRunning( boolean b )
        {
            mRun = b;
        }
        public void setState( DucFireworkSceneAnimateState state )
        {
            synchronized ( surfaceHolder )
            {
                this.state = state;
            }
        }

        //Treat this as Update each frame
        public void doDraw( Canvas canvas )
        {
            canvas.drawColor(Misc.backgroundColor);

            //Each fireworkInterval millisecond, shoot another firework
            if (System.currentTimeMillis() - timer >= fireworkInterval && freeFireworkList.size() > 0){
                //Shoot the next free firework if available
                StartFirework(freeFireworkList.getFirst());
                shotFireworkList.add(freeFireworkList.removeFirst());
                timer = System.currentTimeMillis();
            }

            //Redraw all the firework
            for(Firework firework : shotFireworkList){
                firework.doDraw(canvas, paint);
            }

            //Check for free firework
            for(int indx = shotFireworkList.size() - 1; indx >= 0; indx--){
                if (shotFireworkList.get(indx).state == DucAnimationState.Done){
                    shotFireworkList.get(indx).state = DucAnimationState.Idling;
                    freeFireworkList.add(shotFireworkList.remove(indx));
                }
            }
        }

        public void setSurfaceSize( int width, int height )
        {
            synchronized ( surfaceHolder )
            {
//                for(int indx = 0; indx < fireworkList.size(); indx++){
//                    fireworkList.get(indx).Reshape(width, height);
//                }
            }
        }
    }

    private GameThread thread;

    @SuppressLint( "HandlerLeak" )
    public FireworkScene( Context context )
    {
        super( context );

        SurfaceHolder holder = getHolder();
        holder.addCallback( this );

        getHolder().addCallback( this );

        thread = new GameThread( holder, context, new Handler() {
            @Override
            public void handleMessage( Message m ) {
            }} );

        setFocusable( true );
    }

    @Override
    public void surfaceChanged( SurfaceHolder holder, int format, int width, int height )
    {
        thread.setSurfaceSize( width, height );
        Misc.CalculateBoundary(width, height);
    }

    @Override
    public void surfaceCreated( SurfaceHolder holder )
    {
        thread.setRunning( true );
        thread.doStart();
        thread.start();
    }

    @Override
    public void surfaceDestroyed( SurfaceHolder holder )
    {
        boolean retry = true;
        thread.setRunning( false );

        while ( retry )
        {
            try
            {
                thread.join();
                retry = false;
            }
            catch ( InterruptedException e )
            {

            }
        }
    }

}
