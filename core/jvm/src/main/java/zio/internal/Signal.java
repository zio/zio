package zio.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.logging.Logger;


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

    private static final Logger LOGGER = Logger.getLogger("zio.internal.Signal");
    private static final SignalHandler SIGNAL_HANDLER;

    static {
        MethodHandle constructorHandle;
        MethodHandle staticMethodHandle;

        final Class<?> signalClass = findClass("sun.misc.Signal");
        final Class<?> signalHandlerClass = findClass("sun.misc.SignalHandler");

        if ((signalClass != null) && (signalHandlerClass != null)) {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            constructorHandle = initSignalConstructorMethodHandle(lookup, signalClass);
            staticMethodHandle = initHandleStaticMethodHandle(lookup, signalClass, signalHandlerClass);
        } else {
            constructorHandle = null;
            staticMethodHandle = null;
        }

        if (signalHandlerClass != null && constructorHandle != null && staticMethodHandle != null) {
            SIGNAL_HANDLER = new SignalHandler.SunMiscSignalHandler(signalHandlerClass, constructorHandle, staticMethodHandle);
        } else {
            SIGNAL_HANDLER = new SignalHandler.NoOpSignalHandler();
            LOGGER.warning("sun.misc.Signal and sun.misc.SignalHandler are not available on this platform. " +
                    "Defaulting to no-op signal handling implementation; " +
                    "ZIO fiber dump functionality might not work as expected.");
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
            MethodHandles.Lookup lookup,
            Class<?> signalClass) {
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
        try {
            final MethodType handleStaticMethodType = MethodType.methodType(signalHandlerClass, signalClass, signalHandlerClass);
            return lookup.findStatic(signalClass, "handle", handleStaticMethodType);
        } catch (Exception e) {
            return null;
        }
    }

    private abstract static class SignalHandler {
        abstract void handle(String signal, Consumer<Object> handler);

        private final static class SunMiscSignalHandler extends SignalHandler {
            Class<?> signalHandlerClass;
            MethodHandle constuctorMethodHandle;
            MethodHandle staticMethodHandle;

            private SunMiscSignalHandler(
                    Class<?> signalHandlerClass,
                    MethodHandle constuctorMethodHandle,
                    MethodHandle staticMethodHandle
            ) {
                assertNotNull(signalHandlerClass, "signalHandlerClass");
                assertNotNull(constuctorMethodHandle, "constuctorMethodHandle");
                assertNotNull(staticMethodHandle, "staticMethodHandle");

                this.signalHandlerClass = signalHandlerClass;
                this.constuctorMethodHandle = constuctorMethodHandle;
                this.staticMethodHandle = staticMethodHandle;
            }

            private static void assertNotNull(Object obj, String message) {
                if (obj == null) {
                    throw new IllegalArgumentException(message + " cannot be null");
                }
            }

            @Override
            void handle(String signal, Consumer<Object> handler) {
                final InvocationHandler invocationHandler = initInvocationHandler(handler);
                final Object proxy = Proxy.newProxyInstance(
                        Signal.class.getClassLoader(),
                        new Class<?>[]{signalHandlerClass},
                        invocationHandler
                );
                try {
                    final Object s = constuctorMethodHandle.invoke(signal);
                    staticMethodHandle.invoke(s, proxy);
                } catch (Throwable err) {
                    // Gracefully degrade to a no-op logging the exception.
                }
            }

            private static InvocationHandler initInvocationHandler(Consumer<Object> handler) {
                return (proxy, method, args) -> {
                    if (args.length >= 1) {
                        handler.accept(args[0]);
                    }
                    return null;
                };
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
