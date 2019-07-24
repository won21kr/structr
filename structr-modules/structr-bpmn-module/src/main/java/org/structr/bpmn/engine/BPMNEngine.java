package org.structr.bpmn.engine;

/**
 */
public class BPMNEngine extends Thread {

	private BPMNProcessSource source = null;
	private boolean running          = false;

	public BPMNEngine(final BPMNProcessSource source) {

		super("BPMNEngine");

		this.setDaemon(true);

		this.source = source;
	}
	
	void startEngine() {
		start();
	}

	void stopEngine() {
		this.running = false;
	}

	@Override
	public void run() {

		this.running = true;

		while (running) {

			for (final BPMNProcess process : source.getActiveProcesses()) {
				
			}

			/**
			 * The engine, the process or the step needs to know what to do and which
			 * of the possible next steps 
			 */

			try { Thread.sleep(1000); } catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
}
