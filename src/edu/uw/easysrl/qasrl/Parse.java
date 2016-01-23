package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;

import java.util.List;
import java.util.Set;

/**
 * Parse.
 * Created by luheng on 1/12/16.
 */
public class Parse {
    public SyntaxTreeNode syntaxTree;
    public List<Category> categories;
    public Set<ResolvedDependency> dependencies;
    public double score;

    public Parse(SyntaxTreeNode syntaxTree, List<Category> categories, Set<ResolvedDependency> dependencies, double score) {
        this.syntaxTree = syntaxTree;
        this.categories = categories;
        this.dependencies = dependencies;
        this.score = score;
    }

    public Parse(SyntaxTreeNode syntaxTree, List<Category> categories, Set<ResolvedDependency> dependencies) {
        this(syntaxTree, categories, dependencies, 1.0);
    }
}
