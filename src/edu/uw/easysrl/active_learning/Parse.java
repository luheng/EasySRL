package edu.uw.easysrl.active_learning;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.List;
import java.util.Set;

/**
 * Parse.
 * Created by luheng on 1/12/16.
 */
public class Parse {
    public List<Category> categories;
    public Set<ResolvedDependency> dependencies;
    public double score;

    public Parse(List<Category> categories, Set<ResolvedDependency> dependencies, double score) {
        this.categories = categories;
        this.dependencies = dependencies;
        this.score = score;
    }

    public Parse(List<Category> categories, Set<ResolvedDependency> dependencies) {
        this(categories, dependencies, 1.0);
    }
}
