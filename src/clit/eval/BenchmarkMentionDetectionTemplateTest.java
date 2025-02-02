package clit.eval;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aksw.gerbil.transfer.nif.Document;

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
    for (Document nif : nifDocs) {
      String textWithEscapedQuotes = nif.getText().replace("\"", "\\\"");
      System.out.println(textWithEscapedQuotes);
      String experimentData = "{" +
          "\"linkerConfigs\":[{" +
          "\"exampleId\":\"md_combined_cged\"," +
          "\"displayName\":\"MD + combined CG-ED\"," +
          "\"id\":" + 1 + "," +
          "\"pipelineConfigType\":\"complex\"," +

          "\"components\":{\"md\":[{\"id\":\"MD1\",\"value\":\"" + linker + "\"}],\"cg_ed\":[]}," +

          "\"connections\":[]," +
          "\"startComponents\":[\"MD1\"]," +
          "\"endComponents\":[\"MD1\"]" +
          "}]," +
          "\"knowledgeBases\":[]," +
          "\"inputTexts\":[\"" + textWithEscapedQuotes + "\"]" +
          "}";

      String hashedText = this.hashStringSHA256(textWithEscapedQuotes);
      String[] resultsPath = { "MDOnly", datasetName, linker, hashedText };
      ExperimentStore es = new ExperimentStore();

      if (es.getFile(resultsPath).exists()) {
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
            for (Mention mnt : mentions) {
              System.out.println(mnt.getMention());
            }

          }
        }

    }

  }

  public static String hashStringSHA256(String input) {
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
