package com.layout.boss.function_test;
import java.util.LinkedList;
import java.util.Random;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Message;
import android.support.v4.graphics.ColorUtils;
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
    static boolean isDebugging = false;
    private static int backgroundAlpha = 255;            //For fun and effect :D
    static int backgroundColor = Color.WHITE ;
    static float screenHeight;
    static float screenWidth;

    //Firework parameters
    //Timing
    static long minDuration = 2000;
    static long maxDuration = 3000;
    static int minFragment = 50;
    static int maxFragment = 100;
    static float minFireworkRadius = 20;
    static float maxFireworkRadius = 30;    //Radius of the firework in pixel
    //Coordination
        //Min and colorMax possible spawn and target positions
    static Vector2D minPos;
    static Vector2D maxPos;

    //Color stuff
        //Contrast to white: 100 -> 150
    private static int colorMin = 100;
    private static int colorMax = 170;
    private static int colorRange = colorMax - colorMin;

    static void CalculateBoundary(int width, int height){
        screenHeight = height;
        screenWidth = width;

        minPos = new Vector2D(screenWidth * 0.3f, screenHeight * 0.1f);
        maxPos = new Vector2D(screenWidth * 0.7f, screenHeight * 0.5f);
    }
    static void ChangeBackgroundAlpha(int alpha){
        if (alpha < 0 || alpha > 255){
            ColorUtils.setAlphaComponent(backgroundColor, backgroundAlpha);
        } else {
            ColorUtils.setAlphaComponent(backgroundColor, alpha);
        }
    }
    static void ResetBackgroundAlpha(){
        backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, backgroundAlpha);
    }

    //Scale number from one col orRange to another
    static float MapRange(float unscaledNum, float minAllowed, float maxAllowed, float min, float max) {
        return (maxAllowed - minAllowed) * (unscaledNum - min) / (max - min) + minAllowed;
    }
    //Random color
    static int RandomColor(Random random){
        int red = random.nextInt() % colorRange + colorMin;
        int blue = random.nextInt() % colorRange + colorMin;
        int green = random.nextInt() % colorRange + colorMin;
        return Color.rgb(red, green, blue);
    }

    //Check whether 2 coords are near each other within acceptable colorRange
    static boolean Near(float parm1, float parm2, float epsilon){
        return Math.abs(parm1 - parm2) <= epsilon;
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
    static double EaseOutCirc(float t, float b, float c, float d) {
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return c * Math.sqrt(1 - t*t) + b;
    }
    static double EaseOutQuart(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return -c * (t*t*t*t - 1) + b;
    }
    static double EaseOutCubic(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return c*(t*t*t + 1) + b;
    }
    static double LinearTween(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        return c*t/d + b;
    }
    static double EaseInQuint(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        return c*Math.pow(t, 4) + b;
    }
    static double EaseOutQuint(float t, float b, float c, float d){
        if (t >= d){
            return c + b;
        }
        t /= d;
        t--;
        return c*(t*t*t*t*t + 1) + b;
    }
}

class Vector2D{
    float x;
    float y;
    Vector2D(float x, float y){
        this.x = x;
        this.y = y;
    }

    //Some special matrix
    static Vector2D[] GetStandardRotationMatrix(float angle){
        angle = (float) Math.toRadians(angle);
        return new Vector2D[] {new Vector2D((float) Math.cos(angle), (float) Math.sin(angle)),
                               new Vector2D((float)-Math.sin(angle), (float) Math.cos(angle))};
    }

    void Add(Vector2D force){
        x += force.x;
        y += force.y;
    }
    void Mult(float parm){
        x *= parm;
        y *= parm;
    }
    //Right side matrix multiplication -> used for transformation
    void Mult(Vector2D[] matrix){
        if (matrix.length != 2){
            return;
        }

        x = matrix[0].x * x + matrix[1].x * y;
        y = matrix[0].y * x + matrix[1].y * y;
    }
    void Mult(float parm1, float parm2){
        x *= parm1;
        y *= parm2;
    }

    Vector2D Clone(){
        return new Vector2D(x, y);
    }
}

class Firework{

    //Basic info
    private long fireworkDuration = 1500;    //How long does it take for the firework to get to the destination? In millisecond
    private float radius = 5;
    private int color;
    public DucAnimationState state = DucAnimationState.Idling;
    private Random random;

    private Vector2D startPos;
    private Vector2D endPos;
    private long startTime;

    //Fragment info
    private Fragment[] fragmentList;
    private int numberOfFragment;

    Firework(){
        random = new Random();
        fragmentList = new Fragment[Misc.maxFragment];
        for(int indx = 0; indx < fragmentList.length; indx++){
            fragmentList[indx] = new Fragment(random);
        }
    }

    void StartFirework(){
        //Randomize stuff
        this.fireworkDuration = Math.abs(random.nextLong() % (Misc.maxDuration - Misc.minDuration)) + Misc.minDuration;
        this.radius = random.nextFloat() * (Misc.maxFireworkRadius - Misc.minFireworkRadius) + Misc.minFireworkRadius;
        //Since at first, when the view has not been fully initialized, all the colorMax and colorMin width and height are 0
        //  therefore, we need to + 1 to make the colorRange [0, bound) works ( [0, 0) will break the program!)
        float targetX = random.nextInt((int) (Misc.maxPos.x - (Misc.maxPos.x == 0 ? -1 : Misc.minPos.x))) + Misc.minPos.x;
        float targetY = random.nextInt((int) (Misc.maxPos.y - (Misc.maxPos.y == 0 ? -1 : Misc.minPos.y))) + Misc.minPos.y;

        //startPos.x is randomize between 2 x boundary of the screen
        //startPos.y is at the bottom of the screen
        this.startPos = new Vector2D(random.nextInt((int) (Misc.maxPos.x - (Misc.maxPos.x == 0 ? -1 : Misc.minPos.x))) + Misc.minPos.x,
                                       Misc.screenHeight);
        this.endPos = new Vector2D(targetX, targetY);
        this.startTime = System.currentTimeMillis();

        //Alpha is always at colorMax
        color = Misc.RandomColor(random);

        //FOR DEBUGGING PURPOSE
        if (Misc.isDebugging){
            startPos = new Vector2D(500, 0);
            color = Color.WHITE;
            this.radius = 5;
        }

        //Start moving
        state = DucAnimationState.Moving;
    }
    private void Explode(float centerX, float centerY){
        numberOfFragment = random.nextInt(Misc.maxFragment == Misc.minFragment ? 1 : Misc.maxFragment - Misc.minFragment) + Misc.minFragment;
        state = DucAnimationState.Exploding;

        for(int indx = 0; indx < numberOfFragment; indx++){
            float radius = this.radius * (Misc.MapRange(random.nextFloat(), 0.2f, 0.5f, 0, 1));
            //float radius = this.radius * 0.3f;
            fragmentList[indx].StartFragment(new Vector2D(centerX, centerY), radius);
        }
    }

    //Fragment thingy
    private boolean CheckFreeFragments(){
        for(Fragment fragment : fragmentList){
            if (fragment.state != DucAnimationState.Done){
                return false;
            }
        }
        return true;
    }

    void doDraw(Canvas canvas, Paint paint){
        switch(state){
            case Moving:
                //Simple, draw a line until we reach destination :D
                paint.setColor(color);

                long curTime = System.currentTimeMillis() - startTime;
                float nextX = (float) Misc.LinearTween(curTime, startPos.x, endPos.x - startPos.x, fireworkDuration);
                float nextY = (float) Misc.EaseOutQuint(curTime, startPos.y, endPos.y - startPos.y, fireworkDuration);

                if ((Misc.Near(nextX, endPos.x, 100) && Misc.Near(nextY, endPos.y, 20)) || curTime > fireworkDuration){
                    paint.setColor(Misc.backgroundColor);
                    Explode(nextX, nextY);
                }

                canvas.drawCircle(nextX, nextY, radius, paint);
                break;
            case Exploding:
                for (int indx = 0; indx < numberOfFragment; indx++) {
                    fragmentList[indx].doDraw(canvas, paint);
                }

                if (CheckFreeFragments()){
                    //All fragment finish moving
                    state = DucAnimationState.Done;
                    numberOfFragment = 0;
                }
                break;
        }
    }
}

class Fragment{
    //Credit to: https://codepen.io/rajatkantinandi/pen/bQNedV
    //Basic info
    public DucAnimationState state = DucAnimationState.Done;

    private Vector2D vel;           //Velocity of the fragment
    private Vector2D acc;           //Acceleration of the fragment
    private Vector2D pos;           //Current position

    private float radius = 2;
    private long duration = 0;
    private long startTime = 0;

    private int color;
    private Random random;

    //For random shape
    enum Shape{
        Circle, Triangle, Square;

        public static Shape GetRandomShape(Random random){
            return values()[random.nextInt(values().length)];
        }
    }
    private Shape curShape;
    private float curRotation;              //Current rotation of the shape
    private Vector2D[] baseVertexList;

    Fragment(Random random){
        this.random = random;
        acc = new Vector2D(0, 1);  //Gravity!
        baseVertexList = new Vector2D[4];//{new Vector2D(0, 0), new Vector2D(0, 0), new Vector2D(0, 0), new Vector2D(0, 0)};
    }

    void StartFragment(Vector2D startPos, float radius){
        pos = startPos;
//        vel = new Vector2D((random.nextInt(50) + 3) * (random.nextInt(2) * 2 - 1),
//                           (random.nextInt(50) + 3) * (random.nextInt(2) * 2 - 1));
        vel = new Vector2D(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);     //Normalized vector for direction
        vel.Mult(random.nextInt(61) + 10);                                       //Multiply by the length

        this.radius = radius;
        this.duration = (long) ((random.nextLong() % (Misc.maxFragment - Misc.minDuration) + Misc.minDuration) * 0.2f);

        this.startTime = System.currentTimeMillis();

        //Alpha is always at colorMax
        color = Misc.RandomColor(random);

        //Randomize shape that will be drawn
        curShape = Shape.GetRandomShape(random);
        curRotation = 0;

        switch (curShape){
            case Triangle:
                this.radius *= 2.5f; //Scale up a little bit

                //This will first consider (0, 0) as the centroid's coordination and then translate using the current position
                baseVertexList[0] = new Vector2D(0, this.radius);
                baseVertexList[1] = new Vector2D((float)-Math.sqrt(3) / 2.0f * this.radius, -this.radius / 2.0f);
                baseVertexList[2] = new Vector2D((float) Math.sqrt(3) / 2.0f * this.radius, -this.radius / 2.0f);
                break;
            case Square:
                this.radius *= 3f; //Scale up a little bit

                //The center of the square is (0,0)
                baseVertexList[0] = new Vector2D( this.radius * (float) Math.sqrt(2) / 4.0f, this.radius * (float) Math.sqrt(2) / 4.0f);
                baseVertexList[1] = new Vector2D( this.radius * (float) Math.sqrt(2) / 4.0f, -this.radius * (float) Math.sqrt(2) / 4.0f);
                baseVertexList[2] = new Vector2D( -this.radius * (float) Math.sqrt(2) / 4.0f, -this.radius * (float) Math.sqrt(2) / 4.0f);
                baseVertexList[3] = new Vector2D( -this.radius * (float) Math.sqrt(2) / 4.0f, this.radius * (float) Math.sqrt(2) / 4.0f);
                break;
        }

        state = DucAnimationState.Moving;
    }

    private void DrawCircle(Canvas canvas, Paint paint){
        canvas.drawCircle(pos.x, pos.y, radius, paint);
    }
    private void DrawTriangle(Canvas canvas, Paint paint){
        //Draw a equilateral using the current location as its center. The pos served as the triangle centroid

        Vector2D curPos[] = new Vector2D[]{
                new Vector2D(baseVertexList[0].x, baseVertexList[0].y),
                new Vector2D(baseVertexList[1].x, baseVertexList[1].y),
                new Vector2D(baseVertexList[2].x, baseVertexList[2].y)};
        //Rotate the previous triangle
        curPos[0].Mult(Vector2D.GetStandardRotationMatrix(curRotation));
        curPos[1].Mult(Vector2D.GetStandardRotationMatrix(curRotation));
        curPos[2].Mult(Vector2D.GetStandardRotationMatrix(curRotation));

        //Then translate it
        curPos[0].Add(pos);
        curPos[1].Add(pos);
        curPos[2].Add(pos);

        //And draw it out!
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(curPos[0].x, curPos[0].y);
        path.lineTo(curPos[1].x, curPos[1].y);
        path.lineTo(curPos[2].x, curPos[2].y);
        path.lineTo(curPos[0].x, curPos[0].y);

        canvas.drawPath(path, paint);
    }
    private void DrawSquare(Canvas canvas, Paint paint){
        //Draw square where the center of the square is the curPos
        //  and the radius is the half length of the diagonal line

        Vector2D curPos[] = new Vector2D[]{
                new Vector2D(baseVertexList[0].x, baseVertexList[0].y),
                new Vector2D(baseVertexList[1].x, baseVertexList[1].y),
                new Vector2D(baseVertexList[2].x, baseVertexList[2].y),
                new Vector2D(baseVertexList[3].x, baseVertexList[3].y)};
        //Rotate the previous triangle
        curPos[0].Mult(Vector2D.GetStandardRotationMatrix(curRotation));
        curPos[1].Mult(Vector2D.GetStandardRotationMatrix(curRotation));
        curPos[2].Mult(Vector2D.GetStandardRotationMatrix(curRotation));
        curPos[3].Mult(Vector2D.GetStandardRotationMatrix(curRotation));

        //Then translate it
        curPos[0].Add(pos);
        curPos[1].Add(pos);
        curPos[2].Add(pos);
        curPos[3].Add(pos);

        //And draw it out!
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(curPos[0].x, curPos[0].y);
        path.lineTo(curPos[1].x, curPos[1].y);
        path.lineTo(curPos[2].x, curPos[2].y);
        path.lineTo(curPos[3].x, curPos[3].y);
        path.lineTo(curPos[0].x, curPos[0].y);

        canvas.drawPath(path, paint);
    }
    private void DrawShape(Canvas canvas, Paint paint, int point){
        //Set up the canvas and paint
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAntiAlias(true);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);

        Vector2D standardRotationMatrix[] = Vector2D.GetStandardRotationMatrix(curRotation);
        Vector2D curPos[] = new Vector2D[point];
        
        //Do the special case first
        //Copy the original vertex
        curPos[0] = baseVertexList[0].Clone();
        //Rotate the vertex
        curPos[0].Mult(standardRotationMatrix);
        //Then translate it
        curPos[0].Add(pos);
        //And draw it :D
        path.moveTo(curPos[0].x, curPos[0].y);
        
        for(int indx = 1; indx < point; indx++){
            //Copy the original vertex
            curPos[indx] = baseVertexList[indx].Clone();
            //Rotate the vertex
            curPos[indx].Mult(standardRotationMatrix);
            //Then translate it
            curPos[indx].Add(pos);
            //And draw it :D
            path.lineTo(curPos[indx].x, curPos[indx].y);
        }
        path.lineTo(curPos[0].x, curPos[0].y);
        canvas.drawPath(path, paint);
    }

    void doDraw(Canvas canvas, Paint paint){
        switch(state) {
            case Moving:
                vel.Mult(0.85f);    //Slow down
                vel.Add(acc);   //Add acceleration vector to velocity vector
                pos.Add(vel);   //Add velocity vector to position vector

                //Simple, draw a circle until we reach destination :D
                long curTime = System.currentTimeMillis() - startTime;
                    //Life span of the fragment <=> fragment's alpha
                int lifeSpan = (int) Misc.LinearTween(curTime, 255, -255, duration);
                paint.setColor(color);
                paint.setAlpha(lifeSpan);

                curRotation += 3;
                switch(curShape){
                    case Circle:
                        DrawCircle(canvas, paint);
                        break;
                    case Triangle:
                        DrawShape(canvas, paint, 3);
                        break;
                    case Square:
                        DrawShape(canvas, paint, 4);
                        break;
                }

                if (curTime >= duration || lifeSpan <= 0) {
                    state = DucAnimationState.Done;
                }
                break;
        }
    }
}

class FireworkGun{

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
        LinkedList<Firework> freeFireworkList;  //List of free fireworks
        LinkedList<Firework> shotFireworkList;  //List of shot fireworks
        private long timer;                             //We shoot firework on periodically
        private long fireworkInterval = 1500;

        GameThread( SurfaceHolder surfaceHolder, Context context, Handler handler )
        {
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
            Misc.ResetBackgroundAlpha();

            paint = new Paint();
            paint.setStrokeWidth( 2 / getResources().getDisplayMetrics().density );
            //paint.setColor( Color.TRANSPARENT );
            paint.setColor(Color.BLACK);
            paint.setAntiAlias( true );
        }

        private void StartFirework(Firework firework){
            firework.StartFirework();
        }

        //Overwrite stuff
        void doStart()
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
        void doDraw( Canvas canvas )
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

        void setSurfaceSize( int width, int height )
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
                System.out.println("Can't destroy surface!");
            }
        }
    }

}
