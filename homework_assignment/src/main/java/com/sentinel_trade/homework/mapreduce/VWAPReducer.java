package com.sentinel_trade.homework.mapreduce;

import java.io.IOException;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * Reducer: 接收指定 Symbol 的所有 Price 和 Volume，计算总成交量与 VWAP(成交量加权平均价)
 */
public class VWAPReducer extends Reducer<Text, Text, Text, Text> {
    private Text result = new Text();

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        double totalTurnover = 0.0; // 总成交额 (Price * Volume)
        double totalVolume = 0.0;   // 总成交量

        for (Text val : values) {
            String[] pv = val.toString().split(",");
            try {
                double price = Double.parseDouble(pv[0]);
                double volume = Double.parseDouble(pv[1]);

                totalTurnover += (price * volume);
                totalVolume += volume;
            } catch (NumberFormatException e) {
                // 忽略解析错误的数据
            }
        }

        if (totalVolume > 0) {
            double vwap = totalTurnover / totalVolume; // 计算加权平均价
            // 格式化输出: 总成交量, 加权平均价
            String outputStr = String.format("Total Volume: %.2f, VWAP: %.4f", totalVolume, vwap);
            result.set(outputStr);
            context.write(key, result);
        }
    }
}
