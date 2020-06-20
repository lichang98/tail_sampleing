package me.lc.service;

import me.lc.bean.MiddleProcessorResponseBean;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MiddleService {
    MiddleProcessorResponseBean getBadTraces();

    Map<String, List<String>> getRelatedTraces(Set<String> traceIds);
}
