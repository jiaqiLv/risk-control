package com.risk.sim.eval;

import lombok.Data;

/**
 * Evaluation report for fraud detection model.
 */
@Data
public class EvaluationReport {

    // Confusion matrix
    private long totalSamples;
    private long truePositives;
    private long trueNegatives;
    private long falsePositives;
    private long falseNegatives;

    // Metrics
    private double accuracy;
    private double precision;
    private double recall;
    private double f1Score;
    private double falsePositiveRate;
    private double falseNegativeRate;
    private double trueNegativeRate;

    // Advanced metrics
    private double auc;  // Area Under ROC Curve
    private double ks;   // Kolmogorov-Smirnov statistic

    /**
     * Get a summary of the evaluation report.
     *
     * @return Summary string
     */
    public String getSummary() {
        return String.format(
                "Evaluation Report: Total=%d, TP=%d, TN=%d, FP=%d, FN=%d, " +
                        "Acc=%.2f%%, Prec=%.2f%%, Rec=%.2f%%, F1=%.4f, AUC=%.4f, KS=%.4f",
                totalSamples, truePositives, trueNegatives, falsePositives, falseNegatives,
                accuracy * 100, precision * 100, recall * 100, f1Score, auc, ks
        );
    }
}
