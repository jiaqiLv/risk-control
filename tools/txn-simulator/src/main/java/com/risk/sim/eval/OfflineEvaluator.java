package com.risk.sim.eval;

import com.risk.sim.metrics.ResultSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline evaluator for fraud detection model performance.
 * Calculates metrics like AUC, KS, precision, recall, etc.
 */
@Slf4j
@Component
public class OfflineEvaluator {

    /**
     * Evaluate model performance using confusion matrix metrics.
     *
     * @param resultSink Result sink with collected statistics
     * @return Evaluation report
     */
    public EvaluationReport evaluate(ResultSink resultSink) {
        // This is a placeholder - the actual statistics are in ResultSink
        // In a real implementation, you would read the results from the output file
        // or collect them during simulation

        log.info("Starting offline evaluation...");

        EvaluationReport report = new EvaluationReport();
        report.setTotalSamples(0);
        report.setTruePositives(0);
        report.setTrueNegatives(0);
        report.setFalsePositives(0);
        report.setFalseNegatives(0);

        // Calculate metrics
        calculateMetrics(report);

        log.info("Offline evaluation completed: {}", report.getSummary());

        return report;
    }

    /**
     * Calculate evaluation metrics.
     *
     * @param report Evaluation report to populate
     */
    private void calculateMetrics(EvaluationReport report) {
        long tp = report.getTruePositives();
        long tn = report.getTrueNegatives();
        long fp = report.getFalsePositives();
        long fn = report.getFalseNegatives();

        // Accuracy
        double accuracy = (tp + tn) / (double) (tp + tn + fp + fn);
        report.setAccuracy(accuracy);

        // Precision
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        report.setPrecision(precision);

        // Recall (True Positive Rate)
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        report.setRecall(recall);

        // F1 Score
        double f1 = (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;
        report.setF1Score(f1);

        // False Positive Rate
        double fpr = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0.0;
        report.setFalsePositiveRate(fpr);

        // False Negative Rate
        double fnr = (fn + tp) > 0 ? (double) fn / (fn + tp) : 0.0;
        report.setFalseNegativeRate(fnr);

        // True Negative Rate (Specificity)
        double tnr = 1.0 - fpr;
        report.setTrueNegativeRate(tnr);
    }

    /**
     * Calculate AUC (Area Under ROC Curve).
     * This requires a list of (score, label) pairs.
     *
     * @param scores List of prediction scores
     * @param labels List of true labels (0 or 1)
     * @return AUC value
     */
    public double calculateAUC(List<Double> scores, List<Boolean> labels) {
        if (scores.size() != labels.size() || scores.isEmpty()) {
            log.warn("Invalid input for AUC calculation");
            return 0.0;
        }

        // Sort by score descending
        List<ScoreLabelPair> pairs = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            pairs.add(new ScoreLabelPair(scores.get(i), labels.get(i)));
        }

        pairs.sort((a, b) -> Double.compare(b.score, a.score));

        // Calculate AUC using trapezoidal rule
        int n = pairs.size();
        int positiveCount = 0;
        int negativeCount = 0;

        for (ScoreLabelPair pair : pairs) {
            if (pair.label) {
                positiveCount++;
            } else {
                negativeCount++;
            }
        }

        if (positiveCount == 0 || negativeCount == 0) {
            return 0.0;
        }

        double auc = 0.0;
        int rank = 1;

        for (ScoreLabelPair pair : pairs) {
            if (pair.label) {
                auc += rank - (positiveCount + 1.0) / 2.0;
            }
            rank++;
        }

        auc /= (positiveCount * negativeCount);

        return auc;
    }

    /**
     * Calculate KS statistic (Kolmogorov-Smirnov).
     * Measures the maximum separation between fraud and legit distributions.
     *
     * @param fraudScores   List of fraud scores
     * @param legitScores   List of legit scores
     * @param numBuckets    Number of buckets for KS calculation
     * @return KS statistic
     */
    public double calculateKS(List<Double> fraudScores, List<Double> legitScores, int numBuckets) {
        // Create histograms
        int[] fraudHistogram = new int[numBuckets];
        int[] legitHistogram = new int[numBuckets];

        for (double score : fraudScores) {
            int bucket = (int) (score * numBuckets);
            if (bucket >= numBuckets) bucket = numBuckets - 1;
            fraudHistogram[bucket]++;
        }

        for (double score : legitScores) {
            int bucket = (int) (score * numBuckets);
            if (bucket >= numBuckets) bucket = numBuckets - 1;
            legitHistogram[bucket]++;
        }

        // Calculate cumulative distributions
        double[] fraudCumulative = new double[numBuckets];
        double[] legitCumulative = new double[numBuckets];

        double fraudSum = 0.0;
        double legitSum = 0.0;

        for (int i = 0; i < numBuckets; i++) {
            fraudSum += fraudHistogram[i];
            legitSum += legitHistogram[i];

            fraudCumulative[i] = fraudSum / fraudScores.size();
            legitCumulative[i] = legitSum / legitScores.size();
        }

        // Find maximum separation
        double maxSeparation = 0.0;
        for (int i = 0; i < numBuckets; i++) {
            double separation = Math.abs(fraudCumulative[i] - legitCumulative[i]);
            maxSeparation = Math.max(maxSeparation, separation);
        }

        return maxSeparation;
    }

    /**
     * Generate a detailed evaluation report.
     *
     * @return Report string
     */
    public String generateReport(EvaluationReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n========================================\n");
        sb.append("        FRAUD DETECTION EVALUATION      \n");
        sb.append("========================================\n\n");

        sb.append("Confusion Matrix:\n");
        sb.append(String.format("  True Positives:  %d\n", report.getTruePositives()));
        sb.append(String.format("  True Negatives:  %d\n", report.getTrueNegatives()));
        sb.append(String.format("  False Positives: %d\n", report.getFalsePositives()));
        sb.append(String.format("  False Negatives: %d\n\n", report.getFalseNegatives()));

        sb.append("Metrics:\n");
        sb.append(String.format("  Accuracy:        %.4f (%.2f%%)\n", report.getAccuracy(), report.getAccuracy() * 100));
        sb.append(String.format("  Precision:       %.4f (%.2f%%)\n", report.getPrecision(), report.getPrecision() * 100));
        sb.append(String.format("  Recall (TPR):    %.4f (%.2f%%)\n", report.getRecall(), report.getRecall() * 100));
        sb.append(String.format("  F1 Score:        %.4f\n", report.getF1Score()));
        sb.append(String.format("  False Positive Rate: %.4f (%.2f%%)\n", report.getFalsePositiveRate(), report.getFalsePositiveRate() * 100));
        sb.append(String.format("  False Negative Rate: %.4f (%.2f%%)\n", report.getFalseNegativeRate(), report.getFalseNegativeRate() * 100));
        sb.append(String.format("  True Negative Rate:  %.4f (%.2f%%)\n", report.getTrueNegativeRate(), report.getTrueNegativeRate() * 100));

        if (report.getAuc() > 0) {
            sb.append(String.format("  AUC:             %.4f\n", report.getAuc()));
        }

        if (report.getKs() > 0) {
            sb.append(String.format("  KS Statistic:    %.4f\n", report.getKs()));
        }

        sb.append("\n========================================\n");

        return sb.toString();
    }

    private static class ScoreLabelPair {
        double score;
        boolean label;

        ScoreLabelPair(double score, boolean label) {
            this.score = score;
            this.label = label;
        }
    }
}
