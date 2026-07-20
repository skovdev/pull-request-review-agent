package local.agent.pullrequestreviewagent.git;

import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitDiffService {

    /**
     * Diffs the two branch tips, but against their common ancestor rather than
     * {@code baseBranch}'s current tip, so commits that landed on the base branch
     * after the review branch was cut don't show up as part of the review.
     */
    public List<ChangedFile> diff(Repository repository, String baseBranch, String reviewBranch) {
        try (RevWalk revWalk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader()) {

            RevCommit baseCommit = revWalk.parseCommit(resolve(repository, baseBranch));
            RevCommit reviewCommit = revWalk.parseCommit(resolve(repository, reviewBranch));
            RevCommit mergeBase = mergeBase(revWalk, baseCommit, reviewCommit);

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, (mergeBase != null ? mergeBase : baseCommit).getTree());
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, reviewCommit.getTree());

            return diffEntries(repository, reader, oldTree, newTree);
        } catch (IOException e) {
            throw new GitRepositoryException(
                    "Failed to compute diff between " + baseBranch + " and " + reviewBranch, e);
        }
    }

    /**
     * Diffs the current working tree (staged, unstaged and untracked changes) against
     * the common ancestor of {@code baseBranch} and HEAD, i.e. what a PR would look like
     * if you committed and pushed right now.
     */
    public List<ChangedFile> diffWorkingTree(Repository repository, String baseBranch) {
        try (RevWalk revWalk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader()) {

            RevCommit baseCommit = revWalk.parseCommit(resolve(repository, baseBranch));
            RevCommit headCommit = revWalk.parseCommit(resolve(repository, Constants.HEAD));
            RevCommit mergeBase = mergeBase(revWalk, baseCommit, headCommit);

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, (mergeBase != null ? mergeBase : baseCommit).getTree());
            FileTreeIterator newTree = new FileTreeIterator(repository);

            return diffEntries(repository, reader, oldTree, newTree);
        } catch (IOException e) {
            throw new GitRepositoryException(
                    "Failed to compute working tree diff against " + baseBranch, e);
        }
    }

    /**
     * Scans and formats every entry with a single {@link DiffFormatter}. Formatting each
     * entry with its own fresh formatter would lose the working-tree content association
     * {@link DiffFormatter#scan} sets up, and re-resolving an untracked file's blob from the
     * repository's object database fails with {@code MissingObjectException} since it was
     * never written there.
     */
    private List<ChangedFile> diffEntries(Repository repository, ObjectReader reader,
                                           AbstractTreeIterator oldTree, AbstractTreeIterator newTree)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<ChangedFile> changedFiles = new ArrayList<>();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            entries = detectRenames(repository, reader, oldTree, newTree, entries);
            for (DiffEntry entry : entries) {
                out.reset();
                formatter.format(entry);
                changedFiles.add(toChangedFile(entry, out.toString(StandardCharsets.UTF_8)));
            }
        }
        return changedFiles;
    }

    /**
     * {@link DiffFormatter}'s own {@code setDetectRenames(true)} runs the detector against a
     * plain {@link ObjectReader}, which fails to resolve content for a working-tree side (e.g. an
     * uncommitted rename with edits) since that content was never written to the object database.
     * Running the detector ourselves with a {@link ContentSource.Pair} built the same way
     * {@link DiffFormatter#scan} builds its own keeps rename/copy detection working for
     * committed-to-committed and committed-to-working-tree diffs alike.
     */
    private List<DiffEntry> detectRenames(Repository repository, ObjectReader reader,
                                           AbstractTreeIterator oldTree, AbstractTreeIterator newTree,
                                           List<DiffEntry> entries) throws IOException {
        RenameDetector detector = new RenameDetector(repository);
        detector.addAll(entries);
        ContentSource.Pair sourcePair = new ContentSource.Pair(
                contentSource(oldTree, reader), contentSource(newTree, reader));
        try {
            return detector.compute(sourcePair, NullProgressMonitor.INSTANCE);
        } catch (CanceledException e) {
            return entries;
        }
    }

    private ContentSource contentSource(AbstractTreeIterator iterator, ObjectReader reader) {
        if (iterator instanceof WorkingTreeIterator workingTreeIterator) {
            return ContentSource.create(workingTreeIterator);
        }
        return ContentSource.create(reader);
    }

    private RevCommit mergeBase(RevWalk revWalk, RevCommit a, RevCommit b) throws IOException {
        revWalk.reset();
        revWalk.setRevFilter(RevFilter.MERGE_BASE);
        revWalk.markStart(revWalk.parseCommit(a.getId()));
        revWalk.markStart(revWalk.parseCommit(b.getId()));
        RevCommit base = revWalk.next();
        revWalk.reset();
        return base;
    }

    private ChangedFile toChangedFile(DiffEntry entry, String diff) {
        String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                ? entry.getOldPath()
                : entry.getNewPath();
        return new ChangedFile(path, mapChangeType(entry.getChangeType()), diff);
    }

    private ObjectId resolve(Repository repository, String branch) {
        return GitRefs.resolve(repository, branch);
    }

    private ChangedFile.ChangeType mapChangeType(DiffEntry.ChangeType changeType) {
        return switch (changeType) {
            case ADD -> ChangedFile.ChangeType.ADDED;
            case MODIFY -> ChangedFile.ChangeType.MODIFIED;
            case DELETE -> ChangedFile.ChangeType.DELETED;
            case RENAME -> ChangedFile.ChangeType.RENAMED;
            case COPY -> ChangedFile.ChangeType.COPIED;
        };
    }
}
