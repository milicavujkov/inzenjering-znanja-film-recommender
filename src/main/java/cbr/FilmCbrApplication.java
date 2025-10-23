package cbr;

import java.util.Collection;

import org.apache.jena.rdf.model.Model;
import ucm.gaia.jcolibri.casebase.LinealCaseBase;
import ucm.gaia.jcolibri.cbraplications.StandardCBRApplication;
import ucm.gaia.jcolibri.cbrcore.Attribute;
import ucm.gaia.jcolibri.cbrcore.CBRCase;
import ucm.gaia.jcolibri.cbrcore.CBRCaseBase;
import ucm.gaia.jcolibri.cbrcore.CBRQuery;
import ucm.gaia.jcolibri.cbrcore.Connector;
import ucm.gaia.jcolibri.exception.ExecutionException;
import ucm.gaia.jcolibri.method.retrieve.RetrievalResult;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.NNConfig;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.NNScoringMethod;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.similarity.global.Average;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.similarity.local.Equal;
import ucm.gaia.jcolibri.method.retrieve.NNretrieval.similarity.local.Interval;
import ucm.gaia.jcolibri.method.retrieve.selection.SelectCases;

public class FilmCbrApplication implements StandardCBRApplication {

    Connector _connector;
    CBRCaseBase _caseBase;
    NNConfig simConfig;

    public FilmCbrApplication(Model model) {
        _connector = new JenaOntologyConnector(model);
        _caseBase = new LinealCaseBase();
    }

    @Override
    public void configure() throws ExecutionException {
        simConfig = new NNConfig();
        simConfig.setDescriptionSimFunction(new Average());

        // 1. genre similarity - 28% weight
        simConfig.addMapping(new Attribute("genres", CaseDescription.class), new SetSimilarity());
        simConfig.setWeight(new Attribute("genres", CaseDescription.class), 0.28);

        // 2. director similarity - 23% weight
        simConfig.addMapping(new Attribute("director", CaseDescription.class), new Equal());
        simConfig.setWeight(new Attribute("director", CaseDescription.class), 0.23);

        // 3. actor similarity - 19% weight
        simConfig.addMapping(new Attribute("actors", CaseDescription.class), new SetSimilarity());
        simConfig.setWeight(new Attribute("actors", CaseDescription.class), 0.19);

        // 4. IMDb rating similarity - 15% weight (interval 0-10)
        simConfig.addMapping(new Attribute("imdbRating", CaseDescription.class), new Interval(10));
        simConfig.setWeight(new Attribute("imdbRating", CaseDescription.class), 0.15);

        // 5. year similarity - 10% weight (interval 100 years)
        simConfig.addMapping(new Attribute("year", CaseDescription.class), new Interval(100));
        simConfig.setWeight(new Attribute("year", CaseDescription.class), 0.10);

        // 6. language similarity - 5% weight
        simConfig.addMapping(new Attribute("languages", CaseDescription.class), new SetSimilarity());
        simConfig.setWeight(new Attribute("languages", CaseDescription.class), 0.05);
    }

    @Override
    public CBRCaseBase preCycle() throws ExecutionException {
        _caseBase.init(_connector);
        return _caseBase;
    }

    @Override
    public void cycle(CBRQuery query) throws ExecutionException {
    }

    @Override
    public void postCycle() throws ExecutionException {
    }

    public Collection<RetrievalResult> findSimilarFilms(String filmTitle, int topN) throws ExecutionException {
        CBRQuery query = null;

        for (CBRCase cbrCase : _caseBase.getCases()) {
            CaseDescription desc = (CaseDescription) cbrCase.getDescription();
            if (desc.getTitle().equalsIgnoreCase(filmTitle)) {
                query = new CBRQuery();
                query.setDescription(desc);
                break;
            }
        }

        if (query == null) {
            return null;
        }

        // retrieves similar cases using k-NN
        Collection<RetrievalResult> eval = NNScoringMethod.evaluateSimilarity(
                _caseBase.getCases(), query, simConfig);

        // removes the query film from results (similarity = 1.0)
        eval.removeIf(result -> {
            CaseDescription desc = (CaseDescription) result.get_case().getDescription();
            return desc.getTitle().equalsIgnoreCase(filmTitle);
        });

        return SelectCases.selectTopKRR(eval, topN);
    }
}