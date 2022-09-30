package constantin.renderingx.example.stepcounter;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by inseok on 4/3/17.
 */

public class MovingAverage {
    //    private ArrayDeque<Float> mBuf;
    private LinkedList<Float> mBuf;
    private int mSize;
    public MovingAverage(int size){
//        mBuf = new ArrayDeque<Float>(size);
//        mBuf = Collections.synchronizedList(new LinkedList<Float>());
        mBuf = new LinkedList<Float>();
        mSize = size;
    }

    public MovingAverage addSample(float value){
        synchronized (mBuf){
            if (mBuf.size() >= mSize){
                mBuf.removeFirst();
            }
            mBuf.addLast(value);
        }
        return this;
    }

    public float peekEarliest(){
        synchronized (mBuf) {
            Float first = mBuf.peekFirst();
            if (first == null) {
                return Float.NaN;
            } else {
                return first;
            }
        }
    }

    public float peekLatest(){
        synchronized (mBuf) {
            Float last = mBuf.peekLast();
            if (last == null) {
                return Float.NaN;
            } else {
                return last;
            }
        }
    }

    public float getAverage(){
        float avg = 0.0f;
        synchronized (mBuf){
            if (mBuf.size() > 0) {
                Iterator<Float> iter = mBuf.iterator();
                while (iter.hasNext()) {
                    avg += iter.next();
                }
                avg /= mBuf.size();
            }
        }
        return avg;
    }
}
