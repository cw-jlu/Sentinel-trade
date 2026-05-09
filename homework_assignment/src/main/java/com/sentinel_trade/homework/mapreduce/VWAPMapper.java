package com.sentinel_trade.homework.mapreduce;

import java.io.IOException;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * Mapper: 解析金融行情 CSV 数据，提取 Symbol、Price 和 Volume
 */
public class VWAPMapper extends Mapper<LongWritable, Text, Text, Text> {
    private Text symbolKey = new Text();
    private Text priceVolumeVal = new Text();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        // 跳过 CSV 表头
        if (key.get() == 0 && value.toString().contains("timestamp")) {
            return;
        }

        // 输入行格式: timestamp, tradeId, symbol, price, volume
        String[] fields = value.toString().split(",");
        if (fields.length >= 5) {
            String symbol = fields[2].trim();
            String price = fields[3].trim();
            String volume = fields[4].trim();
            
            symbolKey.set(symbol);
            // 输出 Value: "Price,Volume"
            priceVolumeVal.set(price + "," + volume);
            context.write(symbolKey, priceVolumeVal);
        }
    }
}
