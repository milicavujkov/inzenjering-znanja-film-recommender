package cbr;

import java.util.HashSet;
import java.util.Set;

import ucm.gaia.jcolibri.exception.NoApplicableSimilarityFunctionException;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.similarity.LocalSimilarityFunction;

public class SetSimilarity implements LocalSimilarityFunction {

    @Override
    public double compute(Object value1, Object value2) throws NoApplicableSimilarityFunctionException {
        if (!(value1 instanceof String) || !(value2 instanceof String)) {
            return 0;
        }

        String str1 = (String) value1;  // target film
        String str2 = (String) value2;  // candidate film

        if (str1.isEmpty() || str2.isEmpty()) {
            return 0;
        }

        Set<String> targetSet = new HashSet<>();
        Set<String> candidateSet = new HashSet<>();

        for (String item : str1.split(",")) {
            targetSet.add(item.trim());
        }

        for (String item : str2.split(",")) {
            candidateSet.add(item.trim());
        }

        Set<String> intersection = new HashSet<>(targetSet);
        intersection.retainAll(candidateSet);

        int targetCount = targetSet.size();
        int commonCount = intersection.size();

        double result;
        if (targetCount == 1) {
            // target has only 1 item, if candidate has it then 100% if not then 0%
            result = commonCount >= 1 ? 1.0 : 0.0;
        } else {
            // target has 2+ items if candidate has 0 common then 0%, 1 common means 50%, 2+ common means 100%
            if (commonCount == 0) {
                result = 0.0;
            } else if (commonCount == 1) {
                result = 0.5;
            } else {
                result = 1.0;
            }
        }

        return result;
    }

    @Override
    public boolean isApplicable(Object value1, Object value2) {
        return value1 instanceof String && value2 instanceof String;
    }
}