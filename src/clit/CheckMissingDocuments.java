package clit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.UnexpectedException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aksw.gerbil.transfer.nif.Document;
import org.hsqldb.lib.StringInputStream;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Lists;

import clit.eval.BenchmarkMentionDetectionTemplateTest;
import experiment.Experiment;
import experiment.ExperimentTask;
import structure.datatypes.AnnotatedDocument;
import structure.datatypes.Mention;
import structure.utils.NIFUtils;

public class CheckMissingDocuments {

	public static void main(String[] args) {
		final String basePath = "C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\CLiT_executions"
				+ "\\RSS-500.ttl";// +"\\MDOnly\\RSS-500.ttl";
		final String inPath = "C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\RSS-500.ttl";
		final String dumpFilePath = basePath + "/consistency_analysis";
		final File file = new File(inPath);
		// Tracks hash <-> document
		final Map<String, String> mapHashDocument = new HashMap<>();
		// Tracks system <-> filepath
		final Map<String, String> mapSystemPath = new HashMap<>();

		// Tracks inconsistencies of system -> list of hashes
		final Map<String, List<String>> mapSystemHashes = new HashMap<>();

		// Add all systems we want to check for consistency here
		mapSystemPath.put("Babelfy", basePath + "/Babelfy");
		mapSystemPath.put("DBpediaSpotlight", basePath + "/DBpediaSpotlight");
		mapSystemPath.put("Falcon 2.0", basePath + "/Falcon 2.0");
		mapSystemPath.put("OpenTapioca", basePath + "/OpenTapioca");
		mapSystemPath.put("Refined MD (.properties)", basePath + "/Refined MD (.properties)");
		mapSystemPath.put("REL MD (.properties)", basePath + "/REL MD (.properties)");
		mapSystemPath.put("REL", basePath + "/REL");
		mapSystemPath.put("spaCy", basePath + "/spaCy");
		mapSystemPath.put("Spacy MD (.properties)", basePath + "/Spacy MD (.properties)");
		mapSystemPath.put("TagMe", basePath + "/TagMe");
		mapSystemPath.put("TextRazor", basePath + "/TextRazor");
		int inconsistencyCounter = 0;
		// Init lists for simplicity regarding null-checks
		for (Map.Entry<String, String> e : mapSystemPath.entrySet()) {
			mapSystemHashes.put(e.getKey(), Lists.newArrayList());
		}

		// Go through each NIF document, get the hash and map it
		try {
			for (Document document : NIFUtils.parseDocuments(file)) {
				final String hash = BenchmarkMentionDetectionTemplateTest.hashStringSHA256(document.getText());
				mapHashDocument.put(hash, document.getText());
				// Check for inconsistencies within file tree
				final Map<String, List<String>> inconsistencies = checkConsistency(mapSystemPath, hash);
				for (Map.Entry<String, List<String>> e : mapSystemHashes.entrySet()) {
					// Concatenate them all
					final List<String> lstInconsistency = inconsistencies.get(e.getKey());
					if (lstInconsistency != null) {
						e.getValue().addAll(lstInconsistency);
						// System.out.println("Found one!");
						inconsistencyCounter += lstInconsistency.size();
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		for (Map.Entry<String, List<String>> e : mapSystemHashes.entrySet()) {
			// Output the missing hashes for each system
			try (BufferedWriter bw = new BufferedWriter(
					new FileWriter(new File(dumpFilePath + "_hash_" + e.getKey() + ".txt")))) {
				for (String hash : e.getValue()) {
					bw.write(hash);
					bw.newLine();
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			// Output the appropriate texts for missing execution files for each system
			try (BufferedWriter bw = new BufferedWriter(
					new FileWriter(new File(dumpFilePath + "_text_" + e.getKey() + ".txt")))) {
				for (String hash : e.getValue()) {
					bw.write(mapHashDocument.get(hash));
					bw.newLine();
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		System.out.println("[TOTAL] Found inconsistencies: " + inconsistencyCounter);

	}

	private static Map<String, List<String>> checkConsistency(final Map<String, String> mapSystemPath,
			final String hash) {
		final Map<String, List<String>> retMap = new HashMap<>();
		for (Map.Entry<String, String> e : mapSystemPath.entrySet()) {
			final String key = e.getKey();
			// Path where system output is located
			final String val = e.getValue();
			retMap.put(key, Lists.newArrayList());
			final File jsonFile = new File(val + "/" + hash + ".json");
			if (!jsonFile.exists()) {
//				System.out.println("File not found for ["+val+"] and hash["+hash+"]");
				retMap.get(key).add(hash);
			} else {
				try {
					Collection<Mention> mentions = getMentionsFromJSON(jsonFile);
					if (mentions == null || mentions.size() == 0)
					{
						// Means there's no mentions for this document despite there being an output document
						retMap.get(key).add(hash);
					}
					
				} catch (IOException | ParseException e1) {
					e1.printStackTrace();
				}
			}
		}
//		System.out.println("Inconsistencies found here: ["+hash+"]: "+retMap);

		return retMap;
	}

	private static Collection<Mention> getMentionsFromJSON(File jsonFile) throws FileNotFoundException, IOException, ParseException {
		final JSONParser jsonParser = new JSONParser();
		final JSONObject jsonDoc = (JSONObject) jsonParser.parse(new FileReader(jsonFile));
		// final JSONObject jsonDoc = new JSONObject();
		final ObjectReader or = new ObjectMapper().readerFor(Experiment.class);
		Experiment experiment = or.readValue(new StringInputStream(jsonDoc.toJSONString()));

		if (experiment.getExperimentTasks().size() != 1) {
			throw new UnexpectedException("Weird number of experiment tasks detected for evaluation ("
					+ experiment.getExperimentTasks().size() + ")");
		}
		final List<ExperimentTask> tasks = experiment.getExperimentTasks();

		final ExperimentTask task = tasks.get(0);

		if (task.getDocuments().size() != 1) {
			throw new UnexpectedException("Weird number of documents (1st level) detected for evaluation ("
					+ task.getDocuments().size() + ")");
		}

		final Collection<AnnotatedDocument> docs = task.getDocuments().iterator().next();
		if (docs.size() != 1) {
			throw new UnexpectedException("Weird number of documents (2nd level) detected for evaluation ("
					+ task.getDocuments().size() + ")");
		}

		final AnnotatedDocument doc = docs.iterator().next();
		return doc.getMentions();
	}

}
