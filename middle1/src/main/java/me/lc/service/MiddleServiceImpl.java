package me.lc.service;

import me.lc.Constants;
import me.lc.MiddleProcessor;
import me.lc.bean.MiddleProcessorResponseBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MiddleServiceImpl implements MiddleService {

    public static final Logger LOGGER = LoggerFactory.getLogger(MiddleServiceImpl.class.getName());
    public static int prevGetBatchPos = 0;

    /**
     * get bad traces
     * only get batches before current processing
     *
     * @return
     */
    @Override
    public MiddleProcessorResponseBean getBadTraces() {
        if (MiddleProcessor.READ_FINISH) {
            // send all
            MiddleProcessorResponseBean responseBean = new MiddleProcessorResponseBean();
            responseBean.setClientId("1");
            responseBean.setIsLast("True");
            Map<String, List<String>> responseResult = new HashMap<>();
            while (prevGetBatchPos <= MiddleProcessor.currProcessingBadPos) {
                Map<String, List<String>> traces = MiddleProcessor.batchesBadTraces.get(prevGetBatchPos);
                for (Map.Entry<String, List<String>> ele : traces.entrySet()) {
                    List<String> spans = responseResult.get(ele.getKey());
                    if (spans == null) {
                        responseResult.put(ele.getKey(), ele.getValue());
                    } else {
                        spans.addAll(ele.getValue());
                    }
                }
                MiddleProcessor.batchesBadTraces.set(prevGetBatchPos, null);
                prevGetBatchPos++;
            }
            LOGGER.info("Middle 1 read finish response bad traces size = "+responseResult.size());
            responseBean.setBadTraces(responseResult);
            return responseBean;
        } else {
            if (prevGetBatchPos >= MiddleProcessor.currProcessingBadPos) {
                LOGGER.info("Middle 1 response bad traces null");
                return null;
            } else {
                // send batches before current processing
                MiddleProcessorResponseBean responseBean = new MiddleProcessorResponseBean();
                responseBean.setClientId("1");
                responseBean.setIsLast("False");
                Map<String, List<String>> responseResult = new HashMap<>();
                while (prevGetBatchPos < MiddleProcessor.currProcessingBadPos) {
                    Map<String, List<String>> traces = MiddleProcessor.batchesBadTraces.get(prevGetBatchPos);
                    for (Map.Entry<String, List<String>> ele : traces.entrySet()) {
                        List<String> spans = responseResult.get(ele.getKey());
                        if (spans == null) {
                            responseResult.put(ele.getKey(), ele.getValue());
                        } else {
                            spans.addAll(ele.getValue());
                        }
                    }
                    MiddleProcessor.batchesBadTraces.set(prevGetBatchPos, null);
                    prevGetBatchPos++;
                }
                responseBean.setBadTraces(responseResult);
                LOGGER.info("Middle 1 response bad traces size = "+responseResult.size());
                return responseBean;
            }
        }
    }

    /**
     * response related traces
     *
     * @param traceIds
     * @return
     */
    @Override
    public Map<String, List<String>> getRelatedTraces(Set<String> traceIds) {
        int relatedMinPos = Integer.MAX_VALUE;

        if (MiddleProcessor.READ_FINISH) {
            Map<String, List<String>> responseResult = new HashMap<>();
            for (String idStr : traceIds) {
                int startPos = MiddleProcessor.normalBatchDropPos, endPos = MiddleProcessor.currProcessingNormalPos;
                List<String> newEntry = new ArrayList<>();
                for (int i = startPos; i <= endPos; ++i) {
                    List<String> spans = MiddleProcessor.batchesNormalTraces.get(i).get(idStr);
                    if (spans != null && !spans.isEmpty()) {
                        newEntry.addAll(spans);
                        relatedMinPos = Math.min(relatedMinPos,i); // find the related minimum position
                    }
                    MiddleProcessor.batchesNormalTraces.get(i).remove(idStr);
                }
                if (!newEntry.isEmpty()) {
                    responseResult.put(idStr, newEntry);
                }
            }
            LOGGER.info("middle 1 read finish response related traces, size="+responseResult.size());
            return responseResult;
        } else {
            Map<String, List<String>> responseResult = new HashMap<>();
            for (String idStr : traceIds) {
                int startPos = MiddleProcessor.normalBatchDropPos, endPos = MiddleProcessor.currProcessingNormalPos - 1;
                List<String> newEntry = new ArrayList<>();
                for (int i = startPos; i <= endPos; ++i) {
                    List<String> spans = MiddleProcessor.batchesNormalTraces.get(i).get(idStr);
                    if (spans != null && !spans.isEmpty()) {
                        newEntry.addAll(spans);
                        relatedMinPos = Math.min(relatedMinPos,i);
                    }
                    MiddleProcessor.batchesNormalTraces.get(i).remove(idStr);
                }
                if (!newEntry.isEmpty()) {
                    responseResult.put(idStr, newEntry);
                }
            }
            // drop normal batches not in capture window
//            int windowLow = relatedMinPos-Constants.MAX_QUEUE_SIZE/Constants.BATCH_SIZE*3/2;
//            while(MiddleProcessor.normalBatchDropPos < MiddleProcessor.currProcessingNormalPos
//                    && MiddleProcessor.normalBatchDropPos < windowLow){
//                MiddleProcessor.batchesNormalTraces.set(MiddleProcessor.normalBatchDropPos,null);
//                MiddleProcessor.normalBatchDropPos++;
//            }
            LOGGER.info("middle 1 response related traces, size="+responseResult.size());
            return responseResult;
        }
    }
}
