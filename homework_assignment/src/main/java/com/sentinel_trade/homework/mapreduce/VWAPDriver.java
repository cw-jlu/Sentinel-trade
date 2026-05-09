package com.sentinel_trade.homework.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Driver: MapReduce 作业的执行入口，用于评估本地跑批数据的执行
 */
public class VWAPDriver {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: VWAPDriver <input path> <output path>");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        // 在本地模式下运行，便于评估作业
        conf.set("mapreduce.framework.name", "local");

        Job job = Job.getInstance(conf, "Calculate VWAP (Volume Weighted Average Price)");

        job.setJarByClass(VWAPDriver.class);
        job.setMapperClass(VWAPMapper.class);
        job.setReducerClass(VWAPReducer.class);

        // 设置输出类型
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // 设置输入输出路径
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.out.println("Starting job with input: " + args[0] + " and output: " + args[1]);
        boolean success = job.waitForCompletion(true);
        System.exit(success ? 0 : 1);
    }
}
