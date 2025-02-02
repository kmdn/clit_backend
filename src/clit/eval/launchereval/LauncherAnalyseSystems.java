package clit.eval.launchereval;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.UnexpectedException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.gerbil.transfer.nif.Document;
import org.hsqldb.lib.StringInputStream;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;

import clit.eval.NIFBaseEvaluator;
import clit.eval.datatypes.evaluation.BaseMetricContainer;
import clit.eval.interfaces.AnnotationEvaluation;
import clit.translator.TranslatorWikipediaToDBpediaFast;
import experiment.Experiment;
import experiment.ExperimentStore;
import experiment.ExperimentTask;
import structure.datatypes.AnnotatedDocument;
import structure.interfaces.clit.Translator;

public class LauncherAnalyseSystems {
	// Load gold standard
	// NIF Docs --> load from KORE50
	final static String[] inPathNIFDatasets = new String[] {
			"C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\conll_aida-yago2-dataset\\AIDA-YAGO2-dataset.tsv_nif",
			"C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\KORE_50_NIF/KORE_50_DBpedia.ttl",
			"C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\" + "News-100.ttl",
			"C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\" + "RSS-500.ttl",
			"C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\entity_linking\\" + "Reuters-128.ttl" };

	public static void main(String[] args) {
		final String goldStandardPath = inPathNIFDatasets[0];
		final String dir = "C:\\Users\\wf7467\\Desktop\\Evaluation Datasets\\Datasets\\CLiT_executions\\MDOnly";
		final Set<String> datasets = new HashSet<>();
		final Set<String> systems = new HashSet<>();
		final Set<String> documents = new HashSet<>();
		final HashBiMap<String, String> hashtextMapping = HashBiMap.create();
		boolean FILL_FOR_CONSISTENCY = true;
		try {

			final Map<String, JSONObject> resultMap = new LauncherAnalyseSystems().load_results(dir, datasets, systems,
					documents);
			System.out.println("Explored Datasets: " + datasets);
			System.out.println("Explored Systems: " + systems);
			System.out.println("Hashes: " + documents);
			final Map<String, AnnotatedDocument> documentMap = extractDocumentFromResults(resultMap);
			System.out.println("Doc count: " + documentMap.size());

			// Create a hash <-to-> text mapping for ordering with NIF gold standard
			// documents
			for (Map.Entry<String, AnnotatedDocument> e : documentMap.entrySet()) {
				final String hash = extractHashFromKey(e.getKey());
				hashtextMapping.put(hash, e.getValue().getText());
			}

			// Go through dataset
			final List<Document> nifDocs = loadDataset(goldStandardPath);

			int counter = 0;
			for (Document doc : nifDocs) {
				if (hashtextMapping.inverse().get(doc.getText()) != null) {
					// System.out.println("Found mapping --> " +
					// hashtextMapping.inverse().get(doc.getText()));
					counter++;
				}
			}
			if (counter != nifDocs.size()) {
				throw new UnexpectedException("Missing " + (nifDocs.size() - counter) + " mappings. (Found: " + counter
						+ " / " + nifDocs.size() + ")");
			} else {
				System.out.println("Found all (" + counter + " / " + nifDocs.size() + ") documents mapped into files.");
			}

			// A map to track (in correct order) the documents
			// KEY= System; VAL= aggregated list of relevant AnnotatedDocument
			final Map<String, List<AnnotatedDocument>> mapSystemDocumentlist = new HashMap<>();
			for (Document doc : nifDocs) {
				final String docHash = hashtextMapping.inverse().get(doc.getText());
				// ASSUMPTION: A document's hash is unique across all datasets (might be wrong
				// in datasets with overlap...)
				for (String dataset : datasets) {
					for (String system : systems) {
						final String keyDataSystemDoc = getFullKey(dataset, system, docHash);
						final String keyDataSystem = getSystemKey(dataset, system);
						AnnotatedDocument systemAnnDoc = documentMap.get(keyDataSystemDoc);
						if (systemAnnDoc != null) {
							// We found the right annotated document for this system, so let's put it into
							// the appropriate list within the tracking map!
							List<AnnotatedDocument> listSystemAnnDocs;
							if ((listSystemAnnDocs = mapSystemDocumentlist.get(keyDataSystem)) == null) {
								listSystemAnnDocs = Lists.newArrayList();
								mapSystemDocumentlist.put(keyDataSystem, listSystemAnnDocs);
							}
							listSystemAnnDocs.add(systemAnnDoc);
						} else {
							// For the current dataset and system, there is no applicable document, so move
							// on to the next one

							// If you leave inconsistencies in, just let it be
							continue;
						}
					}

				}
			}

			// Remove systems that are too inconsistent
			final int MAX_MISSING_DOCS = 100;
			final Map<String, Set<String>> hashInconsistencyTrackingMap = consistencyCheckDocumentAmount(
					MAX_MISSING_DOCS, nifDocs, hashtextMapping, mapSystemDocumentlist);

			final List<String> hashesList = getNIFHashesList(hashtextMapping, nifDocs);

			bufferInconsistentEntries(MAX_MISSING_DOCS, hashInconsistencyTrackingMap, hashesList,
					mapSystemDocumentlist);

			System.out.println("Remaining systems (post 'consistency check'): " + mapSystemDocumentlist.keySet() + " ("
					+ mapSystemDocumentlist.keySet().size() + ")");

			evaluate(nifDocs, mapSystemDocumentlist);

		} catch (IOException |

				ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void bufferInconsistentEntries(final int MAX_MISSING_DOCS,
			Map<String, Set<String>> hashInconsistencyTrackingMap, List<String> hashesList,
			Map<String, List<AnnotatedDocument>> mapSystemDocumentlist) {
		for (Entry<String, Set<String>> e : hashInconsistencyTrackingMap.entrySet()) {
			if (e.getValue().size() == 0 || e.getValue().size() > MAX_MISSING_DOCS) {
				continue;
			}

			// There's a few insonsistencies (less than our set limit)
			// Goal: Render lists consistent even if slightly "wrong" results-wise
			// Step 1: Get the hash
			// Step 2: Figure out what position it belongs to (check NIFDocs perhaps?)
			// Step 3: Add an empty document at that spot
			for (String hash : e.getValue()) {
				final String systemDoc = e.getKey();
				int index = hashesList.indexOf(hash);
				final List<AnnotatedDocument> resultDocList = mapSystemDocumentlist.get(systemDoc);
				// Add a dummy document to resolve
				resultDocList.add(index, new AnnotatedDocument(""));
			}
		}
	}

	/**
	 * Checks for consistency based on number of result documents for each system
	 * 
	 * @param nifDocs
	 * @param hashtextMapping
	 * @param mapSystemDocumentlist
	 * @return
	 * @throws UnexpectedException
	 */
	private static Map<String, Set<String>> consistencyCheckDocumentAmount(final int DELETE_ENTRY_THRESHOLD_MAX_LIMIT,
			final List<Document> nifDocs, HashBiMap<String, String> hashtextMapping,
			Map<String, List<AnnotatedDocument>> mapSystemDocumentlist) throws UnexpectedException {
		System.out
				.println("Consistency check: Were all documents annotated by all systems or did some not go through?");
		final Map<String, Set<String>> hashInconsistencyTrackingMap = new HashMap<>();

		final Iterator<Entry<String, List<AnnotatedDocument>>> iter = mapSystemDocumentlist.entrySet().iterator();
		while (iter.hasNext()) {
			final Entry<String, List<AnnotatedDocument>> e = iter.next();

			if (nifDocs.size() != e.getValue().size()) {
				System.out
						.println("Iconsistency: " + e.getKey() + " - invalid number of annotated documents (Expected: "
								+ nifDocs.size() + ", Found: " + e.getValue().size() + "). Removing from pool.");

				// Checking which documents are missing
				final HashSet<String> inconsistencySetText = new HashSet<>(getAllTextsFromNIF(nifDocs));
				inconsistencySetText.removeAll(new HashSet<>(getAllTexts(e.getValue())));
				Set<String> inconsistencySetHash = getHashes(inconsistencySetText, hashtextMapping);
				hashInconsistencyTrackingMap.put(e.getKey(), inconsistencySetHash);
				if (inconsistencySetHash.size() > DELETE_ENTRY_THRESHOLD_MAX_LIMIT) {
					iter.remove();
				}
			}
		}

		// Outputting which are missing
		for (Entry<String, Set<String>> e : hashInconsistencyTrackingMap.entrySet()) {
			int max_display_limit = 100;
			if (e.getValue().size() < max_display_limit) {
				System.out.println(
						"System[" + e.getKey() + "]: Missing Hashes(" + e.getValue().size() + "): " + e.getValue());
			} else {
				System.out.println("System[" + e.getKey() + "]: Missing Hashes(" + e.getValue().size() + "): >"
						+ max_display_limit);
				System.out.println(Lists.newArrayList(e.getValue()).subList(0, max_display_limit));
			}
		}

		return hashInconsistencyTrackingMap;
	}

	private static List<String> getNIFHashesList(HashBiMap<String, String> hashtextMapping, List<Document> nifDocs)
			throws UnexpectedException {
		List<String> ret = Lists.newArrayList();
		for (Document doc : nifDocs) {
			final String hash = hashtextMapping.inverse().get(doc.getText());
			if (hash == null) {
				throw new UnexpectedException("Could not map NIF doc to appropriate hash...");
			}
			ret.add(hash);
		}
		return ret;
	}

	private static Set<String> getHashes(HashSet<String> inconsistencySetText,
			HashBiMap<String, String> hashtextMapping) throws UnexpectedException {
		Set<String> hashes = new HashSet<>();
		for (String inconsistencyText : inconsistencySetText) {
			final String hash = hashtextMapping.inverse().get(inconsistencyText);
			if (hash == null) {
				throw new UnexpectedException("Could not find hash for text: '" + inconsistencyText + "'");
			} else {
				hashes.add(hash);
			}
		}
		return hashes;
	}

	private static Set<String> getAllTextsFromNIF(List<Document> nifDocs) {
		Set<String> ret = new HashSet<>();
		for (Document doc : nifDocs) {
			ret.add(doc.getText());
		}

		return ret;
	}

	private static List<String> getAllTexts(List<AnnotatedDocument> value) {
		List<String> ret = Lists.newArrayList();
		for (AnnotatedDocument doc : value) {
			ret.add(doc.getText());
		}
		return ret;
	}

	private static void evaluate(List<Document> nifDocs, Map<String, List<AnnotatedDocument>> mapSystemDocumentlist) {
		int markingsSum = 0;
		for (int i = 0; i < nifDocs.size(); ++i) {
			final Document goldDoc = nifDocs.get(i);
			markingsSum += goldDoc.getMarkings().size();
		}

		for (Map.Entry<String, List<AnnotatedDocument>> e : mapSystemDocumentlist.entrySet()) {
			// Annotated documents
			final List<AnnotatedDocument> annotatedDocs = e.getValue();
			final String datasetSystemName = e.getKey();
			// Keep track of everyone's scores, so we can aggregate them...
			final Map<String, BaseMetricContainer> mapLinkerMetricContainers = new HashMap<>();

			final Map<String, List<AnnotatedDocument>> mapLinkerResults = new HashMap<>();
			final Map<String, Integer> mapMentionTextTP = new HashMap<>();
			final Map<String, Integer> mapMentionTextFP = new HashMap<>();
			final Map<String, Integer> mapMentionTextFN = new HashMap<>();

			// Evaluate annotated documents
			final List<AnnotationEvaluation> evaluations = new NIFBaseEvaluator().evaluate(nifDocs, annotatedDocs);

			// printEvaluations(evaluations);

			// Add metrics to a container and keep track of it via map
			final BaseMetricContainer linkerMetricContainer = LauncherEvaluatorTesterMentions
					.addMetricsToContainer(evaluations);
			mapLinkerMetricContainers.put(datasetSystemName, linkerMetricContainer);

			final List<Integer> mentionTruthMetricValues = LauncherEvaluatorTesterMentions
					.computeMentionTP_FP_FN(evaluations);
			final int mentionTextTP = mentionTruthMetricValues.get(0);
			final int mentionTextFP = mentionTruthMetricValues.get(1);
			final int mentionTextFN = mentionTruthMetricValues.get(2);

			mapMentionTextTP.put(datasetSystemName, mentionTextTP);
			mapMentionTextFP.put(datasetSystemName, mentionTextFP);
			mapMentionTextFN.put(datasetSystemName, mentionTextFN);
			System.out.println("THIS MENTION");
			System.out.println("Total annotations / mentions in document: (TP + FN =) " + markingsSum);
			System.out.println("TP:" + mentionTextTP + " FN:" + mentionTextFN + " FP:" + mentionTextFP);
			System.out.println("NEXT MENTION");
			System.out.println(
					"--------------------------------------- NEXT LINKER'S EVAL ------------------------------------------");
		}

		// mapEvaluations = combineSystemsPairwise(linkerStringDelim, nifDocs,
		// mapLinkerResults, combiner);

	}

	private static List<Document> loadDataset(String inPathNIFDataset) throws FileNotFoundException {
		final File inFile = new File(inPathNIFDataset);
		final Translator documentTranslator = new TranslatorWikipediaToDBpediaFast();

		if (!inFile.exists()) {
			throw new FileNotFoundException("Could not find the evaluation input file at: " + inFile.getAbsolutePath());
		}

		// Read in documents and translate them (e.g. from DBpedia to Wikidata) if
		// needed
		final List<Document> nifDocs = LauncherEvaluatorTesterMentions.loadDocumentsAndTranslate(inFile,
				documentTranslator);
		return nifDocs;
	}

	private static Map<String, AnnotatedDocument> extractDocumentFromResults(Map<String, JSONObject> resultMap)
			throws IOException {
		final Map<String, AnnotatedDocument> mentionMap = new HashMap<>();
		for (Map.Entry<String, JSONObject> e : resultMap.entrySet()) {
			final JSONObject jsonDoc = e.getValue();
			// Now transform JSON into AnnotatedDocument
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
			if (mentionMap.get(e.getKey()) != null) {
				throw new UnexpectedException("Mention map (" + mentionMap.size() + " entries) already contains key["
						+ e.getKey() + "]. Text already[" + mentionMap.get(e.getKey()).getText() + "] vs. attempted["
						+ doc.getText() + "]");
			}
			mentionMap.put(e.getKey(), doc);

			// jsonDoc.get
		}
		return mentionMap;
	}

	/**
	 * Loads all results and throws them into a map
	 * 
	 * @param dir       directory where the structure is: DIR > DATASETS > SYSTEMS >
	 *                  DOCUMENTS w/ Results & Metadata (JSON)
	 * @param documents
	 * @param systems
	 * @param datasets
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	public Map<String, JSONObject> load_results(final String dir, Set<String> datasets, Set<String> systems,
			Set<String> documents) throws IOException, ParseException {
		final Map<String, JSONObject> resultMap = new HashMap<>();
		final File[] dataset_folders = new File(dir).listFiles(File::isDirectory);
		for (File dataset : dataset_folders) {
			// Keep track of each dataset name for retrieval
			datasets.add(dataset.getName());
			// For each dataset...
			final Map<String, JSONObject> mapDataset = load_results_for_dataset(dataset, systems, documents);

			// Making sure as not to overwrite values within map accidentally...
			if (!checkDuplicates(mapDataset, resultMap)) {
				// There is no intersection, as such we simply merge the two maps simply.
				resultMap.putAll(mapDataset);
			}
		}
		return resultMap;

	}

	public Map<String, JSONObject> load_results_for_dataset(final File dataset, Set<String> systems,
			Set<String> documents) throws IOException, ParseException {
		final Map<String, JSONObject> resultDatasetMap = new HashMap<>();

		final File[] system_folders = dataset.listFiles(File::isDirectory);
		for (File system : system_folders) {
			// Keep track of each system/linker name for retrieval
			systems.add(system.getName());
			// Load all results (aka. all documents) for this particular system
			final Map<String, JSONObject> resultSystemMap = load_results_for_system(dataset.getName(), system, systems,
					documents);
			if (!checkDuplicates(resultSystemMap, resultDatasetMap)) {
				// There is no intersection, as such we simply merge the two maps simply.
				resultDatasetMap.putAll(resultSystemMap);
			}

		}

		return resultDatasetMap;
	}

	/**
	 * Loads all result documents within a specific system folder within a specific
	 * dataset folder folder
	 * 
	 * @param datasetName
	 * @param system
	 * @param systems
	 * @param documents
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	private Map<String, JSONObject> load_results_for_system(final String datasetName, final File system,
			Set<String> systems, Set<String> documents) throws IOException, ParseException {
		final Map<String, JSONObject> resultSystemMap = new HashMap<>();
		final File[] document_json_files = system.listFiles(File::isFile);
		for (File document : document_json_files) {
			// Keep track of each document name for retrieval
			documents.add(document.getName());
			// Read the JSON document
			final JSONObject result = ExperimentStore.readExperimentResultAsJson(document);
			// Generate unique key for map retrieval
			final String key = getFullKey(datasetName, system.getName(), document.getName());
			// Check for inconsistencies / duplicate
			if (resultSystemMap.get(key) != null) {
				throw new UnexpectedException(
						"Combination of dataset, system and document is not unique. Better look into it! (key = " + key
								+ ")");
			} else {
				// This entry does not yet exist, so let's put it in
				resultSystemMap.put(key, result);
			}
		}
		return resultSystemMap;
	}

	/**
	 * Checks for dupicate keys to prevent overwriting, logical inconsistencies and
	 * data loss
	 * 
	 * @param mapDataset
	 * @param resultMap
	 * @return
	 * @throws UnexpectedException
	 */
	private boolean checkDuplicates(Map<String, JSONObject> mapDataset, Map<String, JSONObject> resultMap)
			throws UnexpectedException {
		final Set<String> intersection = new HashSet<>(resultMap.keySet());
		intersection.retainAll(new HashSet<>(mapDataset.keySet()));
		if (intersection.size() > 0) {
			throw new UnexpectedException(
					"Combination of dataset and overall map: key for dataset & system & document is not unique. Better look into it! (Intersection: "
							+ intersection + ")");
			// return true;
		} else {
			return false;
		}
	}

	private static String getDatasetKey(String dataset) {
		return dataset;
	}

	private static String getSystemKey(String dataset, String system) {
		return getDatasetKey(dataset) + " > " + system;
	}

	private static String getDocumentKey(String dataset, String system, String documentHash) {
		return getSystemKey(dataset, system) + " > " + documentHash;
	}

	private static String getFullKey(String dataset, String system, String documentHash) {
		return getDocumentKey(dataset, system, documentHash);
	}

	private static String getFullKey(String datasetSystem, String documentHash) {
		return getFullKey(datasetSystem.split(" > ")[0], datasetSystem.split(" > ")[1], documentHash);
	}

	private static String extractHashFromKey(String key) {
		final String delim = " > ";
		final int index = key.lastIndexOf(delim);
		return key.substring(index + delim.length());
	}

}
