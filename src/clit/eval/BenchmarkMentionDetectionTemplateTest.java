package clit.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aksw.gerbil.transfer.nif.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.google.common.collect.Lists;

import experiment.Experiment;
import experiment.ExperimentBuilder;
import experiment.ExperimentStore;
import experiment.Experimenter;
import structure.datatypes.AnnotatedDocument;
import structure.datatypes.Mention;
import structure.utils.NIFUtils;



public class BenchmarkMentionDetectionTemplateTest implements Runnable {

	private String datasetPath;
	private String linker;
	private String datasetName;
	private BenchmarkReporter benchmarkReporter;

	public BenchmarkMentionDetectionTemplateTest(String datasetName, String datasetPath, String linker) {
		this.datasetName = datasetName;
		this.datasetPath = datasetPath;
		this.linker = linker;
	}

	public void detect() {

		File file = new File(datasetPath);

		final List<Document> nifDocs = Lists.newArrayList();
		try {
			for (Document document : NIFUtils.parseDocuments(file)) {
				nifDocs.add(document);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.benchmarkReporter = new BenchmarkReporter(datasetName, linker, nifDocs.size());

		for (Document nif : nifDocs) {
			String textWithEscapedQuotes = nif.getText().replace("\"", "\\\"");
			System.out.println(textWithEscapedQuotes);
			String experimentData = "{" + "\"linkerConfigs\":[{" + "\"exampleId\":\"md_combined_cged\","
					+ "\"displayName\":\"MD + combined CG-ED\"," + "\"id\":" + 1 + ","
					+ "\"pipelineConfigType\":\"complex\"," +

					"\"components\":{\"md\":[{\"id\":\"MD1\",\"value\":\"" + linker + "\"}],\"cg_ed\":[]}," +

					"\"connections\":[]," + "\"startComponents\":[\"MD1\"]," + "\"endComponents\":[\"MD1\"]" + "}],"
					+ "\"knowledgeBases\":[]," + "\"inputTexts\":[\"" + textWithEscapedQuotes + "\"]" + "}";

			String hashedText = this.hashStringSHA256(textWithEscapedQuotes);

			this.benchmarkReporter.insertTextHash(textWithEscapedQuotes, hashedText);

			String[] resultsPath = { "MDOnly", datasetName, linker, hashedText };
			ExperimentStore es = new ExperimentStore();

			File resultFile = es.getFile(resultsPath);
			if (resultFile.exists()) {
				try {
					JSONObject jsonObject = (JSONObject) ExperimentStore.readExperimentResultAsJson(resultFile);

					JSONArray experimentTasksArray = (JSONArray) jsonObject.get("experimentTasks");

					JSONObject firstTask = (JSONObject) experimentTasksArray.get(0);

					JSONArray documentsArray = (JSONArray) firstTask.get("documents");

					JSONArray firstDocument = (JSONArray) documentsArray.get(0);

					JSONArray mentionsArray = (JSONArray) ((JSONObject) firstDocument.get(0)).get("mentions");

					int mentionsSize = mentionsArray.size();
					this.benchmarkReporter.updateNumOfMentions(hashedText, mentionsSize);
					this.benchmarkReporter.incrementNumOfProcessedSamples();

				} catch (IOException | ParseException e) {
					e.printStackTrace();
				}
				System.out.println(" ----------- Skipped -------------");
				continue;
			}

			final Experiment experiment = new ExperimentBuilder(experimentData).buildExperiment();

			final Experiment result = new Experimenter(experiment).runPath(resultsPath);

			Collection<Collection<AnnotatedDocument>> cands = result.getExperimentTasks().get(0).getDocuments();
			if (cands != null)
				for (Collection<AnnotatedDocument> ands : cands) {
					for (AnnotatedDocument and : ands) {
						Collection<Mention> mentions = and.getMentions();
						this.benchmarkReporter.updateNumOfMentions(hashedText, mentions.size());
						this.benchmarkReporter.incrementNumOfProcessedSamples();
						System.out.println("made it to hier" + mentions.size());
						for (Mention mnt : mentions) {
							System.out.println(mnt.getMention());
						}

					}
				}
		}

		JSONObject json = null;
		json = convertBenchmarkToJson(benchmarkReporter);
		saveJsonToFile(json, System.getenv("REPORTS") + "/" + datasetName + "_" + linker + ".json");
	}

        @SuppressWarnings("unchecked")
	private static JSONObject convertBenchmarkToJson(BenchmarkReporter benchmarkReporter) {
		JSONObject benchmarkJson = new JSONObject();
		benchmarkJson.put("datasetName", benchmarkReporter.getDatasetName());
		benchmarkJson.put("linkerName", benchmarkReporter.getLinkerName());
		benchmarkJson.put("numProcessedSamples", benchmarkReporter.getNumProcessedSamples());
		benchmarkJson.put("totalNumOfSamples", benchmarkReporter.getTotalNumOfSamples());

		JSONArray textsArray = new JSONArray();
		List<BenchmarkReporter.Text> texts = benchmarkReporter.getTexts();
		for (BenchmarkReporter.Text text : texts) {
			JSONObject textJson = new JSONObject();
			textJson.put("hash", text.hash);
			textJson.put("text", text.text);
			textJson.put("numOfMentions", text.numOfMentions);
			textsArray.add(textJson);
		}

		benchmarkJson.put("texts", textsArray);

		return benchmarkJson;
	}

	private static void saveJsonToFile(JSONObject jsonObject, String fileName) {
		try (FileWriter fileWriter = new FileWriter(fileName)) {
			fileWriter.write(jsonObject.toJSONString());
			System.out.println("JSON saved to: " + fileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String hashStringSHA256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes());

			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void run() {
		this.detect();
	}

}
