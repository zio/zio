package zio.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;


/**
 * This class is used to handle system signals in a platform-specific way. It attemps to use the
 * sun.misc.Signal and sun.misc.SignalHandler classes if they are available. If they are not
 * available, it will gracefully degrade to a no-op.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>: This class uses reflection to access the sun.misc.Signal and sun.misc.SignalHandler.
 * This is necessary because the sun.misc.Signal and sun.misc.SignalHandler classes might not be available in some
 * JVMs.
 * </p>
 */
final class Signal {

    private Signal() {
    }

    private static final MethodHandle SIGNAL_CONSTRUCTOR_METHOD_HANDLE;
    private static final MethodHandle HANDLE_STATIC_METHOD_HANDLE;
    private static final SignalHandler SIGNAL_HANDLER;

    static {
        final Class<?> signalClass = findClass("sun.misc.Signal");
        final Class<?> signalHandlerClass = findClass("sun.misc.SignalHandler");

        if ((signalClass != null) && (signalHandlerClass != null)) {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();

            SIGNAL_CONSTRUCTOR_METHOD_HANDLE = initSignalConstructorMethodHandle(lookup, signalClass);
            HANDLE_STATIC_METHOD_HANDLE = initHandleStaticMethodHandle(lookup, signalClass, signalHandlerClass);
            SIGNAL_HANDLER = new SignalHandler.SunMiscSignalHandler(signalHandlerClass);
        } else {
            System.err.println("WARNING: sun.misc.Signal and sun.misc.SignalHandler are not available on this platform. " +
                    "Custom signal handling and ZIO fiber dump functionality might not work as expected.");
            SIGNAL_CONSTRUCTOR_METHOD_HANDLE = null;
            HANDLE_STATIC_METHOD_HANDLE = null;
            SIGNAL_HANDLER = new SignalHandler.NoOpSignalHandler();
        }
    }

    static void handle(String signal, Consumer<Object> handler) {
        SIGNAL_HANDLER.handle(signal, handler);
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static MethodHandle initSignalConstructorMethodHandle(
            MethodHandles.Lookup lookup, Class<?> signalClass) {
        final MethodType signalConstructorMethodType = MethodType.methodType(void.class, String.class);
        try {
            return lookup.findConstructor(signalClass, signalConstructorMethodType);
        } catch (Exception e) {
            return null;
        }
    }

    private static MethodHandle initHandleStaticMethodHandle(
            MethodHandles.Lookup lookup,
            Class<?> signalClass,
            Class<?> signalHandlerClass) {
        final MethodType handleStaticMethodType =
                MethodType.methodType(
                        signalHandlerClass, signalClass, signalHandlerClass);
        try {
            return lookup.findStatic(signalClass, "handle", handleStaticMethodType);
        } catch (Exception e) {
            return null;
        }
    }

    private abstract static class SignalHandler {
        abstract void handle(String signal, Consumer<Object> handler);

        private final static class SunMiscSignalHandler extends SignalHandler {
            Class<?> signalHandlerClass;

            private SunMiscSignalHandler(Class<?> cls) {
                if (cls == null) {
                    throw new IllegalArgumentException("signalHandlerClass cannot be null");
                }
                signalHandlerClass = cls;
            }

            @Override
            void handle(String signal, Consumer<Object> handler) {
                if (SIGNAL_CONSTRUCTOR_METHOD_HANDLE != null && HANDLE_STATIC_METHOD_HANDLE != null) {
                    final InvocationHandler invocationHandler = (proxy, method, args) -> {
                        if (args.length >= 1) {
                            handler.accept(args[0]);
                        }
                        return null;
                    };
                    final Object proxy = Proxy.newProxyInstance(
                            Signal.class.getClassLoader(),
                            new Class<?>[]{signalHandlerClass},
                            invocationHandler);
                    try {
                        final Object s = SIGNAL_CONSTRUCTOR_METHOD_HANDLE.invoke(signal);
                        HANDLE_STATIC_METHOD_HANDLE.invoke(s, proxy);
                    } catch (Throwable t) {
                        // Gracefully degrade to a no-op.
                    }
                }
            }
        }

        private final static class NoOpSignalHandler extends SignalHandler {
            private NoOpSignalHandler() {
            }

            @Override
            void handle(String signal, Consumer<Object> handler) {
            }
        }
    }
}
