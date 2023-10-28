package minicpbp.util;

import minicpbp.engine.core.IntVar;

public class ArrayUtil {


    public static IntVar[] append(IntVar[] a1, IntVar a) {
        IntVar[] newArray = new IntVar[a1.length +1];
        System.arraycopy(a1,0,newArray, 0,a1.length);
        newArray[a1.length] = a;
        return newArray;
    }

    public static IntVar[] append(IntVar a, IntVar[] a1) {
        IntVar[] newArray = new IntVar[a1.length +1];
        System.arraycopy(a1,0,newArray, 1,a1.length);
        newArray[0] = a;
        return newArray;
    }

    public static IntVar[] append(IntVar[] a1, IntVar[] a2) {
        IntVar[] newArray = new IntVar[a1.length +a2.length];
        System.arraycopy(a1,0,newArray, 0,a1.length);
        System.arraycopy(a2,0,newArray, a1.length,a2.length);
        return newArray;
    }

    public static float[] addByElement(float[] a1, float[] a2){
        float[] newArray = new float[a1.length];
        for (int i = 0; i < a1.length; ++i) {
            newArray[i] = a1[i] + a2[i];
        }
        return newArray;
    }

    public static float[] divideByElement(float[] a, int divisor){
        float[] newArray = new float[a.length];
        for (int i = 0; i < a.length; ++i) {
            newArray[i] = a[i]/divisor;
        }
        return newArray;
    }
}
