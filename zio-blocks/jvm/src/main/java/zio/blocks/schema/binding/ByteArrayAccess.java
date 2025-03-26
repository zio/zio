package zio.blocks.schema.binding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

class ByteArrayAccess {
    private static final VarHandle VH_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_DOUBLE =
            MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_FLOAT =
            MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_SHORT =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_CHAR =
            MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.LITTLE_ENDIAN);

    static void setLong(byte[] buf, int pos, long value) {
        VH_LONG.set(buf, pos, value);
    }

    static long getLong(byte[] buf, int pos) {
        return (long) VH_LONG.get(buf, pos);
    }

    static void setDouble(byte[] buf, int pos, double value) {
        VH_DOUBLE.set(buf, pos, value);
    }

    static double getDouble(byte[] buf, int pos) {
        return (double) VH_DOUBLE.get(buf, pos);
    }

    static void setInt(byte[] buf, int pos, int value) {
        VH_INT.set(buf, pos, value);
    }

    static int getInt(byte[] buf, int pos) {
        return (int) VH_INT.get(buf, pos);
    }

    static void setFloat(byte[] buf, int pos, float value) {
        VH_FLOAT.set(buf, pos, value);
    }

    static float getFloat(byte[] buf, int pos) {
        return (float) VH_FLOAT.get(buf, pos);
    }

    static void setShort(byte[] buf, int pos, short value) {
        VH_SHORT.set(buf, pos, value);
    }

    static short getShort(byte[] buf, int pos) {
        return (short) VH_SHORT.get(buf, pos);
    }

    static void setChar(byte[] buf, int pos, char value) {
        VH_CHAR.set(buf, pos, value);
    }

    static char getChar(byte[] buf, int pos) {
        return (char) VH_CHAR.get(buf, pos);
    }
}
