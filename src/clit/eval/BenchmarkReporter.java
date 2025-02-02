package clit.eval;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkReporter {
	class Text {
		String hash;
		String text;
		int numOfMentions;
		public Text() {
	    }
		public Text(String text, String hash) {
			this.text = text;
			this.hash = hash;
			this.numOfMentions = 0;
		}

		public void incrementNumOfMentions(int newMentions) {
			this.numOfMentions += newMentions;
		}
	}

	private String datasetName;
	private String linkerName;
	private List<Text> texts;
	private int numProcessedSamples;
	private int totalNumOfSamples;

	public BenchmarkReporter(String dataSetName, String linkerName, int totalNumOfSamples) {
		this.datasetName = dataSetName;
		this.linkerName = linkerName;
		this.totalNumOfSamples = totalNumOfSamples;
		this.texts = new ArrayList<Text>();
	}

	public String getLinkerName() {
		return linkerName;
	}

	public void setLinkerName(String linkerName) {
		this.linkerName = linkerName;
	}

	public String getDatasetName() {
		return datasetName;
	}

	public void setDatasetName(String datasetName) {
		this.datasetName = datasetName;
	}

	public int getNumProcessedSamples() {
		return numProcessedSamples;
	}

	public void setNumProcessedSamples(int numProcessedSamples) {
		this.numProcessedSamples = numProcessedSamples;
	}

	public int getTotalNumOfSamples() {
		return totalNumOfSamples;
	}

	public void setTotalNumOfSamples(int totalNumOfSamples) {
		this.totalNumOfSamples = totalNumOfSamples;
	}

	public void insertTextHash(String text, String hash) {
		texts.add(new Text(text, hash));
	}

	public void updateNumOfMentions(String hash, int numberOfNewMentions) {
	    this.texts.stream().filter(t -> t.hash.equals(hash)).findFirst().get().incrementNumOfMentions(numberOfNewMentions);
	}

	public List<Text> getTexts() {
		// TODO Auto-generated method stub
		return this.texts;
	}
	public void incrementNumOfProcessedSamples() {
		this.numProcessedSamples++;
	}
}
