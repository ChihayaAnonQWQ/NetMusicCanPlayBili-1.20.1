package com.zhongbai233.net_music_can_play_bili.util;

/**
 * {@code java.lang.Math#clamp} 的 Java 17 兼容替身。
 *
 * <p><b>为什么需要它</b>：本工程的编译工具链是 JDK 21，而
 * {@code MathCompat.clamp(long,long,long)} / {@code clamp(double,double,double)} 等重载
 * 是 <b>Java 21 才加入</b>的 API。目标运行环境是 <b>Minecraft 1.20.1 + Forge，即 Java 17</b>——
 * 一旦把字节码目标设为 17（本项目已如此设置，见 {@code build.gradle} 的
 * {@code options.release = 17}），javac 就会拒绝这些调用；
 * 即便侥幸编过，在 Java 17 运行时也会抛 {@code NoSuchMethodError}。</p>
 *
 * <p>语义与 JDK 实现保持一致：返回值已被约束在 {@code [min, max]} 区间内；
 * 当 {@code min > max} 时抛 {@link IllegalArgumentException}
 * （与 {@code MathCompat.clamp} 的约定相同，避免把参数写反时静默得到错误结果）。</p>
 */
public final class MathCompat {
    private MathCompat() {
    }

    public static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return value < min ? min : Math.min(value, max);
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        return value < min ? min : Math.min(value, max);
    }
}
