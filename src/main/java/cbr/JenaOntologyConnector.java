package cbr;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import ucm.gaia.jcolibri.cbrcore.CBRCase;
import ucm.gaia.jcolibri.cbrcore.CaseBaseFilter;
import ucm.gaia.jcolibri.cbrcore.Connector;
import ucm.gaia.jcolibri.exception.InitializingException;

public class JenaOntologyConnector implements Connector {

    private Model model;

    public JenaOntologyConnector(Model model) {
        this.model = model;
    }

    @Override
    public Collection<CBRCase> retrieveAllCases() {
        LinkedList<CBRCase> cases = new LinkedList<>();

        String sparql =
                "PREFIX : <http://example.org/films#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?title ?year ?imdb ?director " +
                        "       (GROUP_CONCAT(DISTINCT ?genreName; separator=\",\") AS ?genres) " +
                        "       (GROUP_CONCAT(DISTINCT ?actorName; separator=\",\") AS ?actors) " +
                        "       (GROUP_CONCAT(DISTINCT ?langName; separator=\",\") AS ?languages) " +
                        "WHERE { " +
                        "  ?film rdf:type :Film ; " +
                        "        :title ?title . " +
                        "  OPTIONAL { ?film :releaseYear ?year } " +
                        "  OPTIONAL { ?film :imdbRating ?imdb } " +
                        "  OPTIONAL { ?film :directedBy ?dir . ?dir :personName ?director } " +
                        "  OPTIONAL { ?film :hasGenre ?g . ?g :genreName ?genreName } " +
                        "  OPTIONAL { ?film :hasActor ?a . ?a :personName ?actorName } " +
                        "  OPTIONAL { ?film :spokenInLanguage ?l . ?l :languageName ?langName } " +
                        "} " +
                        "GROUP BY ?film ?title ?year ?imdb ?director";

        try (QueryExecution qexec = QueryExecution.create()
                .query(QueryFactory.create(sparql))
                .model(model)
                .build()) {

            ResultSet rs = qexec.execSelect();

            while (rs.hasNext()) {
                QuerySolution sol = rs.next();

                CBRCase cbrCase = new CBRCase();
                CaseDescription desc = new CaseDescription();

                String title = sol.getLiteral("title").getString();
                desc.setId(title);
                desc.setTitle(title);

                if (sol.contains("year")) {
                    String yearStr = sol.getLiteral("year").getString();
                    desc.setYear(Integer.parseInt(yearStr.substring(0, 4)));
                } else {
                    desc.setYear(2000);
                }

                desc.setImdbRating(sol.contains("imdb") ? sol.getLiteral("imdb").getDouble() : 0.0);
                desc.setDirector(sol.contains("director") ? sol.getLiteral("director").getString() : "");
                desc.setGenres(sol.contains("genres") ? sol.getLiteral("genres").getString() : "");
                if (sol.contains("actors")) {
                    String actorsStr = sol.getLiteral("actors").getString();
                    desc.setActors(actorsStr);
                    //System.out.println("DEBUG Connector - Film: " + title + " | Actors: [" + actorsStr + "]");  // ← DODAJ OVO
                } else {
                    desc.setActors("");
                    //System.out.println("DEBUG Connector - Film: " + title + " | Actors: EMPTY");  // ← DODAJ OVO
                }
                desc.setLanguages(sol.contains("languages") ? sol.getLiteral("languages").getString() : "");

                cbrCase.setDescription(desc);
                cases.add(cbrCase);
            }
        }

        return cases;
    }

    @Override
    public Collection<CBRCase> retrieveSomeCases(CaseBaseFilter filter) { return null; }

    @Override
    public void storeCases(Collection<CBRCase> cases) {}

    @Override
    public void close() {}

    @Override
    public void deleteCases(Collection<CBRCase> cases) {}

    @Override
    public void initFromXMLfile(java.net.URL url) throws InitializingException {}
}