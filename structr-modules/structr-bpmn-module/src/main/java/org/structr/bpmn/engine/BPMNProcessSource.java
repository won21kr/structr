package org.structr.bpmn.engine;

import java.util.List;

/**
 */
public interface BPMNProcessSource {
	
	List<BPMNProcess> getActiveProcesses();
}
