package com.risk.sim.source;

import lombok.Data;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a joined transaction record from IEEE-CIS dataset.
 * Contains fields from both train_transaction.csv and train_identity.csv.
 */
@Data
public class TransactionRecord {

    // Field cache for CSV column mapping
    public static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    static {
        // Pre-load all field mappings for reflection
        for (Field field : TransactionRecord.class.getDeclaredFields()) {
            field.setAccessible(true);
            FIELD_CACHE.put(field.getName().toLowerCase(), field);
            FIELD_CACHE.put(field.getName(), field);
        }
    }

    // Common fields
    private String transactionId;
    private boolean isFraud;

    // Transaction fields
    private Integer transactionDt;
    private BigDecimal transactionAmt;
    private String productCd;  // Changed from Integer to String (e.g., "W", "H", "C", "S", "R")
    private Integer card1;
    private Integer card2;
    private Integer card3;
    private String card4;  // Changed from Integer to String (e.g., "discover", "visa", "mastercard", "credit")
    private Integer card5;
    private Integer card6;
    private Integer addr1;
    private Integer addr2;
    private Double dist1;
    private Double dist2;

    private String pEmailDomain;
    private String rEmailDomain;

    private Integer c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14;
    private Double d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15;
    private String m1, m2, m3, m4, m5, m6, m7, m8, m9;  // Changed from Double to String (e.g., "T", "F", "M2")
    private Integer v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15;
    private Integer v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, v32, v33, v34;
    private Integer v35, v36, v37, v38, v39, v40, v41, v42, v43, v44, v45, v46, v47, v48, v49, v50, v51, v52, v53, v54;
    private Integer v55, v56, v57, v58, v59, v60, v61, v62, v63, v64, v65, v66, v67, v68, v69, v70, v71, v72, v73, v74;
    private Integer v75, v76, v77, v78, v79, v80, v81, v82, v83, v84, v85, v86, v87, v88, v89, v90, v91, v92, v93, v94;
    private Integer v95, v96, v97, v98, v99, v100, v101, v102, v103, v104, v105, v106, v107, v108, v109, v110, v111, v112, v113, v114;
    private Integer v115, v116, v117, v118, v119, v120, v121, v122, v123, v124, v125, v126, v127, v128, v129, v130, v131, v132, v133, v134;
    private Integer v135, v136, v137, v138, v139, v140, v141, v142, v143, v144, v145, v146, v147, v148, v149, v150, v151, v152, v153, v154;
    private Integer v155, v156, v157, v158, v159, v160, v161, v162, v163, v164, v165, v166, v167, v168, v169, v170, v171, v172, v173, v174;
    private Integer v175, v176, v177, v178, v179, v180, v181, v182, v183, v184, v185, v186, v187, v188, v189, v190, v191, v192, v193, v194;
    private Integer v195, v196, v197, v198, v199, v200, v201, v202, v203, v204, v205, v206, v207, v208, v209, v210, v211, v212, v213, v214;
    private Integer v215, v216, v217, v218, v219, v220, v221, v222, v223, v224, v225, v226, v227, v228, v229, v230, v231, v232, v233, v234;
    private Integer v235, v236, v237, v238, v239, v240, v241, v242, v243, v244, v245, v246, v247, v248, v249, v250, v251, v252, v253, v254;
    private Integer v255, v256, v257, v258, v259, v260, v261, v262, v263, v264, v265, v266, v267, v268, v269, v270, v271, v272, v273, v274;
    private Integer v275, v276, v277, v278, v279, v280, v281, v282, v283, v284, v285, v286, v287, v288, v289, v290, v291, v292, v293, v294;
    private Integer v295, v296, v297, v298, v299, v300, v301, v302, v303, v304, v305, v306, v307, v308, v309, v310, v311, v312, v313, v314;
    private Integer v315, v316, v317, v318, v319, v320, v321, v322, v323, v324, v325, v326, v327, v328, v329, v330, v331, v332, v333, v334;
    private Integer v335, v336, v337, v338, v339;

    // Identity fields
    private String id01, id02, id03, id04, id05, id06, id07, id08, id09;
    private Integer id10, id11;
    private String id12, id13, id14, id15, id16, id17, id18, id19, id20, id21, id22;
    private String id23, id24, id25, id26, id27, id28, id29, id30, id31, id32;
    private String id33, id34, id35, id36, id37, id38;
    private Integer deviceType;
    private String deviceInfo;

    /**
     * Calculate a user key for cold start simulation.
     * Uses fields specified in configuration.
     */
    public String calculateUserKey(Map<String, String> userKeyFields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : userKeyFields.entrySet()) {
            String value = getFieldValueByName(entry.getKey());
            if (value != null) {
                sb.append(value).append("_");
            }
        }
        return sb.length() > 0 ? sb.toString() : transactionId;
    }

    /**
     * Get field value by CSV column name (supports auto-mapping)
     */
    private String getFieldValueByName(String fieldName) {
        String javaFieldName = csvColumnToFieldName(fieldName);
        Field field = FIELD_CACHE.get(javaFieldName.toLowerCase());

        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(this);
            return value != null ? value.toString() : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Convert CSV column name to Java field name
     */
    private String csvColumnToFieldName(String csvColumn) {
        // Special mappings for non-standard naming
        return switch (csvColumn) {
            case "TransactionID" -> "transactionId";
            case "ProductCD" -> "productCd";
            case "P_emaildomain" -> "pEmailDomain";
            case "R_emaildomain" -> "rEmailDomain";
            case "DeviceType" -> "deviceType";
            case "DeviceInfo" -> "deviceInfo";
            default -> {
                // Default: convert first character to lowercase
                // Works for: card1, C1, V1, addr1, dist1, etc.
                if (csvColumn == null || csvColumn.isEmpty()) {
                    yield null;
                }
                yield Character.toLowerCase(csvColumn.charAt(0)) + csvColumn.substring(1);
            }
        };
    }

    /**
     * Calculate missing rate for this record.
     * Returns the ratio of null fields to total fields.
     */
    public double calculateMissingRate() {
        int totalFields = 0;
        int nullFields = 0;

        // Count transaction fields
        totalFields += 50; // card1-6, addr1-2, dist1-2, c1-14, d1-15, m1-9, v1-339
        if (card1 == null) nullFields++;
        if (card2 == null) nullFields++;
        if (card3 == null) nullFields++;
        if (card4 == null) nullFields++;
        if (card5 == null) nullFields++;
        if (card6 == null) nullFields++;
        if (addr1 == null) nullFields++;
        if (addr2 == null) nullFields++;
        if (dist1 == null) nullFields++;
        if (dist2 == null) nullFields++;
        // Count V fields (simplified)
        for (int i = 1; i <= 339; i++) {
            totalFields++;
            // V fields check would be here
        }

        return totalFields > 0 ? (double) nullFields / totalFields : 0.0;
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
                "transactionId='" + transactionId + '\'' +
                ", isFraud=" + isFraud +
                ", transactionDt=" + transactionDt +
                ", transactionAmt=" + transactionAmt +
                ", productCd=" + productCd +
                ", card1=" + card1 +
                ", card2=" + card2 +
                ", card3=" + card3 +
                ", card4=" + card4 +
                ", card5=" + card5 +
                ", card6=" + card6 +
                ", addr1=" + addr1 +
                ", addr2=" + addr2 +
                ", dist1=" + dist1 +
                ", dist2=" + dist2 +
                ", pEmailDomain='" + pEmailDomain + '\'' +
                ", rEmailDomain='" + rEmailDomain + '\'' +
                '}';
            }
}
