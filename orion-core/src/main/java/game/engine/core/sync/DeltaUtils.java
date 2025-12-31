package game.engine.core.sync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * 用于计算和应用字节数组 Delta 压缩的工具类。
 * 实现了适用于实体状态同步的简单二进制差分算法。
 */
public class DeltaUtils {

    /**
     * 计算两个字节数组之间的 Delta (差异)。
     * 
     * @param oldState 旧状态。
     * @param newState 新状态。
     * @return 代表差异的字节数组。如果状态相同，返回空数组。
     */
    public static byte[] calculateDelta(byte[] oldState, byte[] newState) {
        if (Arrays.equals(oldState, newState)) {
            return new byte[0];
        }

        // 简单的游程编码 / 差分方法
        // 格式: [OpCode][Data...]
        // OpCodes:
        // 0x00: 从旧数据复制 (长度: 1 字节)
        // 0x01: 插入新数据 (长度: 1 字节, 数据: N 字节)
        // 这是一个非常简化的实现。生产环境建议使用 Bentley-McIlroy 或 Xdelta 等算法。
        // 这里我们实现一个简单的基于块的 XOR 或简单的差分用于演示。

        // 我们实现一个简单的 "变更检测" 方法。
        // 假设状态是一系列字段。
        // 但对于通用 byte[]，如果长度匹配，我们做一个简单的 XOR Delta。
        // 如果长度不同，则返回 newState (全量同步)。

        if (oldState.length != newState.length) {
            // 长度已更改，回退到全量更新 (通过特定标志指示或仅返回带有头部的 newState)
            // 对于这个简单的工具，我们返回一个 "全量更新" 包。
            // 协议: [类型: 1 字节] [载荷]
            // 类型 0: 无变更
            // 类型 1: Delta (XOR) - 仅当长度匹配。
            // 类型 2: 全量状态

            byte[] result = new byte[1 + newState.length];
            result[0] = 2; // 全量状态
            System.arraycopy(newState, 0, result, 1, newState.length);
            return result;
        }

        // 长度匹配，尝试 XOR Delta
        byte[] xorDiff = new byte[newState.length];
        boolean allZero = true;
        for (int i = 0; i < newState.length; i++) {
            xorDiff[i] = (byte) (oldState[i] ^ newState[i]);
            if (xorDiff[i] != 0) {
                allZero = false;
            }
        }

        if (allZero) {
            return new byte[] { 0 }; // 无变更
        }

        // 返回 XOR Delta
        byte[] result = new byte[1 + xorDiff.length];
        result[0] = 1; // XOR Delta
        System.arraycopy(xorDiff, 0, result, 1, xorDiff.length);
        return result;
    }

    /**
     * 将 Delta 应用于旧状态以获取新状态。
     * 
     * @param oldState 旧状态。
     * @param delta    Delta 补丁。
     * @return 新状态。
     */
    public static byte[] applyDelta(byte[] oldState, byte[] delta) {
        if (delta.length == 0) {
            return oldState;
        }

        byte type = delta[0];

        if (type == 0) {
            return oldState;
        } else if (type == 1) { // XOR Delta
            if (oldState.length != delta.length - 1) {
                throw new IllegalArgumentException("XOR 补丁与 Delta 长度不匹配");
            }
            byte[] newState = new byte[oldState.length];
            for (int i = 0; i < oldState.length; i++) {
                newState[i] = (byte) (oldState[i] ^ delta[i + 1]);
            }
            return newState;
        } else if (type == 2) { // 全量状态
            byte[] newState = new byte[delta.length - 1];
            System.arraycopy(delta, 1, newState, 0, newState.length);
            return newState;
        } else {
            throw new IllegalArgumentException("未知的 Delta 类型: " + type);
        }
    }
}
