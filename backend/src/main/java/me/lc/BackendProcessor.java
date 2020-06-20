package me.lc;

import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;
import me.lc.bean.MiddleProcessorResponseBean;
import me.lc.service.MiddleService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BackendProcessor extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendProcessor.class.getName());

    public static Map<String, List<String>> badTraces;

    public static boolean BACKEND_PROCESSOR_READY = false;

    public static boolean client1Finish = false;
    public static boolean client2Finish = false;

    // two middle processor service
    public static MiddleService middleService1;
    public static MiddleService middleService2;

    // bad trace id set for middle processor 1 and middle processor 2
    public static Set<String> setBadTracesWait1;
    public static Set<String> setBadTracesWait2;

    public BackendProcessor() {
        badTraces = new HashMap<>();
        setBadTracesWait1 = new HashSet<>();
        setBadTracesWait2 = new HashSet<>();
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"consumer.xml"});
        context.start();
        middleService1 = (MiddleService) context.getBean("middleService1");
        middleService2 = (MiddleService) context.getBean("middleService2");

        LOGGER.info("Backend processor ready!");
        BACKEND_PROCESSOR_READY = true;
    }

    public static void requestRelatedFromMiddle1() {
        Map<String, List<String>> relatedTrace = middleService1.getRelatedTraces(setBadTracesWait2);
        if (relatedTrace != null && !relatedTrace.isEmpty()) {
            LOGGER.info("backend request related traces from middle 1, size=" + relatedTrace.size());
            for (Map.Entry<String, List<String>> ele : relatedTrace.entrySet()) {
                List<String> spans = badTraces.get(ele.getKey());
                if (spans == null) {
                    badTraces.put(ele.getKey(), ele.getValue());
                } else {
                    spans.addAll(ele.getValue());
                }
            }
        }
    }

    public static void requestRelatedFromMiddle2() {
        Map<String, List<String>> relatedTrace = middleService2.getRelatedTraces(setBadTracesWait1);
        if (relatedTrace != null && !relatedTrace.isEmpty()) {
            LOGGER.info("backend request related traces from middle 2, size=" + relatedTrace.size());
            for (Map.Entry<String, List<String>> ele : relatedTrace.entrySet()) {
                List<String> spans = badTraces.get(ele.getKey());
                if (spans == null) {
                    badTraces.put(ele.getKey(), ele.getValue());
                } else {
                    spans.addAll(ele.getValue());
                }
            }
        }
    }

    public static void getBadTracesFrom1AndReqRelated() {
        MiddleProcessorResponseBean middleProcessorResponseBeanMiddle1 = middleService1.getBadTraces();
        if (middleProcessorResponseBeanMiddle1 != null) {
            Map<String, List<String>> badTracesFromMiddle1 = middleProcessorResponseBeanMiddle1.getBadTraces();
            if (badTracesFromMiddle1 != null && !badTracesFromMiddle1.isEmpty()) {
                LOGGER.info("backend get bad traces from middle 1, size=" + badTracesFromMiddle1.size());
                for (Map.Entry<String, List<String>> ele : badTracesFromMiddle1.entrySet()) {
                    List<String> spans = badTraces.get(ele.getKey());
                    if (spans == null) {
                        badTraces.put(ele.getKey(), ele.getValue());
                    } else {
                        spans.addAll(ele.getValue());
                    }
                }
                // request for related traces
                setBadTracesWait1.addAll(badTracesFromMiddle1.keySet());
                requestRelatedFromMiddle2();
            }

            if (middleProcessorResponseBeanMiddle1.getIsLast().equals("True")) {
                client1Finish = true;
            }
        }
    }

    public static void getBadTracesFrom2AndReqRelated() {
        MiddleProcessorResponseBean middleProcessorResponseBeanMiddle2 = middleService2.getBadTraces();
        if (middleProcessorResponseBeanMiddle2 != null) {
            Map<String, List<String>> badTracesFromMiddle2 = middleProcessorResponseBeanMiddle2.getBadTraces();
            if (badTracesFromMiddle2 != null && !badTracesFromMiddle2.isEmpty()) {
                LOGGER.info("backend get bad traces from middle 2, size=" + badTracesFromMiddle2.size());
                for (Map.Entry<String, List<String>> ele : badTracesFromMiddle2.entrySet()) {
                    List<String> spans = badTraces.get(ele.getKey());
                    if (spans == null) {
                        badTraces.put(ele.getKey(), ele.getValue());
                    } else {
                        spans.addAll(ele.getValue());
                    }
                }
                // request related traces
                setBadTracesWait2.addAll(badTracesFromMiddle2.keySet());
                requestRelatedFromMiddle1();
            }
            if (middleProcessorResponseBeanMiddle2.getIsLast().equals("True")) {
                client2Finish = true;
            }
        }
    }

    @SneakyThrows
    @Override
    public void run() {
        while (!client1Finish || !client2Finish) {
            getBadTracesFrom1AndReqRelated();
            getBadTracesFrom2AndReqRelated();
        }
        // request remaining
        TimeUnit.SECONDS.sleep(5);
        getBadTracesFrom1AndReqRelated();
        getBadTracesFrom1AndReqRelated();
        getBadTracesFrom1AndReqRelated();
        getBadTracesFrom1AndReqRelated();
        sendResults();
    }


    /**
     * when finished, send bad traces with MD5 sum
     */
    public void sendResults() throws IOException {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<String>> pair : badTraces.entrySet()) {
            Set<String> spanSet = new HashSet<>(pair.getValue());
            String spans = spanSet.stream()
                    .sorted(Comparator.comparing(BackendProcessor::getStartTime))
                    .collect(Collectors.joining("\n"));
            spans = spans + "\n";
            result.put(pair.getKey(), MD5(spans));
        }
        LOGGER.info("BackendProcessor: send result size=" + result.size());
        // send result
        String jsonResult = JSON.toJSONString(result);
        RequestBody requestBody = new FormBody.Builder()
                .add("result", jsonResult).build();
        String url = String.format("http://localhost:%s/api/finished", Constants.DATA_SOURCE_PORT);
        Request request = new Request.Builder().url(url).post(requestBody).build();
        Call call = Constants.okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
        requestBody = null;
        request = null;
    }

    public static String MD5(String key) {
        char[] hexDigits = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        try {
            byte[] btInput = key.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) {
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }


    public static long getStartTime(String span) {
        if (span != null) {
            String[] cols = span.intern().split("\\|");
            if (cols.length > 8) {
                try {
                    return Long.parseLong(cols[1]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

}
