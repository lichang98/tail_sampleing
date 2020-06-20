package me.lc;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.*;

/**
 * This class run as the middle processor filter and find bad traces
 * only sends data of bad traces to backend processor
 */
public class MiddleProcessor extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiddleProcessor.class.getName());
    public static boolean MIDDLE_PROCESSOR_READY = false;

    public static Queue<String> inputQueue;
    public static boolean READ_FINISH = false;

    public static List<Map<String, List<String>>> batchesBadTraces;
    public static List<Map<String, List<String>>> batchesNormalTraces;

    public static LinkedList<String> uncertainTraceIds; // used in batches control

    public static int currProcessingBadPos = 0;
    public static int currProcessingNormalPos = 0;
    public static int normalBatchDropPos = 0;

    public static int badBatchCurrPosSize = 0;
    public static int normalBatchCurrPosSize = 0;

    public MiddleProcessor() {
        inputQueue = new LinkedList<>();
        batchesBadTraces = new ArrayList<>();
        batchesNormalTraces = new ArrayList<>();
        uncertainTraceIds = new LinkedList<>();

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"provider.xml"});
        context.start();
        LOGGER.info("middle processor 1 ready!");
        MIDDLE_PROCESSOR_READY = true;
    }

    /**
     * when program get data source port, pull data from it
     */
    @SneakyThrows
    @Override
    public void run() {
        URL urlDataSrc = new URL("http://localhost:" + Constants.DATA_SOURCE_PORT + "/trace1.data");
        LOGGER.info("data url=" + urlDataSrc);
        HttpURLConnection httpURLConnection = (HttpURLConnection) urlDataSrc.openConnection(Proxy.NO_PROXY);
        InputStream inputStream = httpURLConnection.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        LOGGER.info("middle processor 1 load data...");
        batchesBadTraces.add(new HashMap<>());
        batchesNormalTraces.add(new HashMap<>());

        String inputLine = null;
        while ((inputLine = bufferedReader.readLine()) != null) {
            // input procedure
            String[] cols = inputLine.intern().split("\\|");
            if (cols.length > 1) {
                String traceId = cols[0].intern();
                String tag = null;
                if (cols.length > 8) {
                    tag = cols[8].intern();
                }
                if (tag != null && tag.intern().contains("error=1") || (tag != null && tag.intern().contains("http.status_code=")
                        && !tag.intern().contains("http.status_code=200"))) {
                    // bad trace
                    List<String> spans = batchesBadTraces.get(currProcessingBadPos).get(traceId.intern());
                    if (spans == null) {
                        // first of this trace
                        batchesBadTraces.get(currProcessingBadPos).put(traceId.intern(), new LinkedList<>(Arrays.asList(inputLine.intern())));
                        badBatchCurrPosSize++;
                        uncertainTraceIds.addLast(traceId.intern()); // only when the start mark out of the queue can be viewed as certain
                        inputQueue.add("zzzz" + inputLine.intern());
                    } else {
                        spans.add(inputLine.intern());
                        inputQueue.add("");
                    }
                } else {
                    List<String> spans = batchesBadTraces.get(currProcessingBadPos).get(traceId.intern());
                    if (spans != null) {
                        spans.add(inputLine.intern());
                        badBatchCurrPosSize++;
                    } else {
                        inputQueue.add(inputLine.intern());
                    }
                }
                tag = null;
                traceId = null;
            }
            inputLine = null;
            Arrays.fill(cols, null);
            if (badBatchCurrPosSize >= Constants.BATCH_SIZE) {
                // next batch, then old batches can be sent to backend processor
                // initial uncertain traceIds
                badBatchCurrPosSize = 0;
                Map<String, List<String>> spans = new HashMap<>();
                for (String traceId : uncertainTraceIds) {
                    spans.put(traceId, new ArrayList<>());
                }
                batchesBadTraces.add(spans);
                currProcessingBadPos++;
            }
            pollAndProcessQueueWhileSize(Constants.MAX_QUEUE_SIZE + Constants.BATCH_SIZE, Constants.MAX_QUEUE_SIZE);
        }
        // processing the reaming data in the input queue
        pollAndProcessQueueWhileSize(0, 0); // poll and process all

        bufferedReader.close();
        inputStream.close();
        LOGGER.info("middle processor 1 read from socket finish");
        READ_FINISH = true;
    }

    /**
     * poll data from queue until queue size not larger than the threshold
     *
     * @param sizeHighThreshold
     * @param sizeLowThreshold
     */
    public static void pollAndProcessQueueWhileSize(int sizeHighThreshold, int sizeLowThreshold) {
        // poll from inputQueue and process
        if (inputQueue.size() >= sizeHighThreshold) {
            int size = inputQueue.size();
            while (size > sizeLowThreshold) {
                size--;
                String traceRecord = inputQueue.poll();
                if (traceRecord != null && traceRecord.intern().startsWith("zzzz")) {
                    // the first of the trace has arrive end of the queue
                    // all related spans have been captured
                    String[] cols = traceRecord.intern().substring(4).split("\\|");
                    uncertainTraceIds.removeFirst(); // this trace Id not uncertain now
                    Arrays.fill(cols, null);
                } else if (traceRecord != null && !traceRecord.equals("")) {
                    String[] cols = traceRecord.intern().split("\\|");
                    List<String> spans = batchesBadTraces.get(currProcessingBadPos).get(cols[0].intern());
                    if (spans != null) {
                        spans.add(traceRecord.intern());
                        badBatchCurrPosSize++;
                        if (badBatchCurrPosSize >= Constants.BATCH_SIZE) {
                            // next batch, then old batches can be sent to backend processor
                            // initial uncertain traceIds
                            badBatchCurrPosSize = 0;
                            Map<String, List<String>> spansNewBad = new HashMap<>();
                            for (String traceId : uncertainTraceIds) {
                                spansNewBad.put(traceId, new ArrayList<>());
                            }
                            batchesBadTraces.add(spansNewBad);
                            currProcessingBadPos++;
                        }
                    } else {
                        // add to normal traces
                        List<String> normalSpans = batchesNormalTraces.get(currProcessingNormalPos).get(cols[0].intern());
                        if (normalSpans == null) {
                            batchesNormalTraces.get(currProcessingNormalPos).put(cols[0].intern(), new ArrayList<>(Arrays.asList(traceRecord.intern())));
                        } else {
                            batchesNormalTraces.get(currProcessingNormalPos).get(cols[0].intern()).add(traceRecord.intern());
                        }
                        normalBatchCurrPosSize++;
                        if (normalBatchCurrPosSize >= Constants.BATCH_SIZE) {
                            // new batch
                            batchesNormalTraces.add(new HashMap<>());
                            currProcessingNormalPos++;
                            normalBatchCurrPosSize = 0;
                        }
                    }
                    Arrays.fill(cols, null);
                }
                traceRecord = null;
            }
        }
    }
}
