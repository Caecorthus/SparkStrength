package annina.sparkstrength.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * The reflection mechanism shared by the optional bridges in this package, which reach the other Spark mods only by
 * name. Each bridge keeps its own policy: its mod-loaded check (run before calling here), when it resolves, its
 * defaults and whether a failure is logged or remembered. Lookups initialise the owner class
 * ({@link Class#forName(String)}) and yield null when the class or method is missing; only JDK types are used, so the
 * local fixture compilers can compile this file next to any bridge.
 * 本包可选桥接共用的反射机制：这些桥接只按名称访问其他 Spark 模组。各桥接保留各自的策略：模组加载检查（在调用此处之前进行）、
 * 解析时机、默认值以及失败是否记录日志或被记住。查找会初始化所属类（{@link Class#forName(String)}），类或方法缺失时返回 null；
 * 只使用 JDK 类型，因此本地夹具编译器可以把本文件与任意桥接一起编译。
 */
final class OptionalReflection {
    private OptionalReflection() {
    }

    /**
     * Public method {@code owner#name(parameters)} with no further check, or null when it cannot be found.
     * 公开方法 {@code owner#name(parameters)}，不做进一步检查；找不到时返回 null。
     */
    static Method method(String owner, String name, Class<?>... parameters) {
        try {
            return Class.forName(owner).getMethod(name, parameters);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            // forName/getMethod throw no other checked exception, so this equals catching ReflectiveOperationException.
            // forName/getMethod 不会抛出其他受检异常，因此与捕获 ReflectiveOperationException 等价。
            return null;
        }
    }

    /**
     * Like {@link #method}, but only a static method whose return type is exactly {@code returnType}; else null.
     * 同 {@link #method}，但只接受返回类型恰为 {@code returnType} 的静态方法；否则返回 null。
     */
    static Method staticMethod(String owner, String name, Class<?> returnType, Class<?>... parameters) {
        Method method = method(owner, name, parameters);
        return method != null && Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType
                ? method : null;
    }

    /**
     * Calls {@code method} with no receiver. Null when {@code method} is null or the call fails with an access,
     * argument, linkage or target exception; anything else propagates, as in the bridges' original copies.
     * 以无接收者方式调用 {@code method}。{@code method} 为 null，或调用因访问、参数、链接或目标异常失败时返回 null；
     * 其他异常照常抛出，与各桥接原先的写法一致。
     */
    static Object invokeStatic(Method method, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }
}
