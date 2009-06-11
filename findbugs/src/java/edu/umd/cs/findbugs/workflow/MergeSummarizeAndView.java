/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2004, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.workflow;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.dom4j.DocumentException;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugRanker;
import edu.umd.cs.findbugs.CommandLineUiCallback;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.IGuiCallback;
import edu.umd.cs.findbugs.PrintingBugReporter;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.ProjectStats;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.cloud.Cloud;
import edu.umd.cs.findbugs.config.CommandLine;
import edu.umd.cs.findbugs.gui2.FindBugsLayoutManagerFactory;
import edu.umd.cs.findbugs.gui2.GUISaveState;
import edu.umd.cs.findbugs.gui2.MainFrame;
import edu.umd.cs.findbugs.gui2.SplitLayout;

/**
 * Compute the union of two sets of bug results, preserving annotations.
 */
public class MergeSummarizeAndView {

	public static class MSVOptions {

		List<String> workingDirList = new ArrayList<String>();

		List<String> analysisFiles = new ArrayList<String>();

		List<String> srcDirList = new ArrayList<String>();

		int maxRank = 12;

		int maxConsideredRank = 14;

		int maxAge = 7;

		boolean alwaysShowGui = false;
	}

	static class MSVCommandLine extends CommandLine {

		final MSVOptions options;

		public MSVCommandLine(MSVOptions options) {
			this.options = options;
			addOption("-workingDir", "filename",
			        "Comma separated list of current working directory paths, used to resolve relative paths (Jar, AuxClasspathEntry, SrcDir)");
			addOption("-srcDir", "filename", "Comma separated list of directory paths, used to resolve relative SourceFile paths");
			addOption("-maxRank", "rank", "maximum rank of issues to show in summary (default 12)");
			addOption("-maxConsideredRank", "rank", "maximum rank of issues to consider (default 14)");
			addOption("-maxAge", "days", "maximum age of issues to show in summary (default 7)");
			addSwitch("-gui", "display GUI for any warnings. Default: Displays GUI for warnings meeting filtering criteria");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * edu.umd.cs.findbugs.config.CommandLine#handleOption(java.lang.String,
		 * java.lang.String)
		 */
		@Override
		protected void handleOption(String option, String optionExtraPart) throws IOException {
			if (option.equals("-gui"))
				options.alwaysShowGui = true;
			else
				throw new IllegalArgumentException("Unknown option : " + option);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * edu.umd.cs.findbugs.config.CommandLine#handleOptionWithArgument(java
		 * .lang.String, java.lang.String)
		 */
		@Override
		protected void handleOptionWithArgument(String option, String argument) throws IOException {
			if (option.equals("-workingDir"))
				options.workingDirList = Arrays.asList(argument.split(","));
			else if (option.equals("-srcDir"))
				options.srcDirList = Arrays.asList(argument.split(","));
			else if (option.equals("-maxRank"))
				options.maxRank = Integer.parseInt(argument);
			else if (option.equals("-maxAge"))
				options.maxAge = Integer.parseInt(argument);
			else
				throw new IllegalArgumentException("Unknown option : " + option);
		}

	}

	static {
		DetectorFactoryCollection.instance(); // as a side effect, loads
		// detector plugins
	}

	static public SortedBugCollection union(SortedBugCollection origCollection, SortedBugCollection newCollection) {

		SortedBugCollection result = origCollection.duplicate();

		for (Iterator<BugInstance> i = newCollection.iterator(); i.hasNext();) {
			BugInstance bugInstance = i.next();
			result.add(bugInstance);
		}
		ProjectStats stats = result.getProjectStats();
		ProjectStats stats2 = newCollection.getProjectStats();
		stats.addStats(stats2);

		Project project = result.getProject();
		project.add(newCollection.getProject());

		return result;
	}

	public static void main(String[] argv) throws Exception {

		final MSVOptions options = new MSVOptions();
		final MSVCommandLine commandLine = new MSVCommandLine(options);

		int argCount = commandLine.parse(argv, 1, Integer.MAX_VALUE, "Usage: " + MergeSummarizeAndView.class.getName()
		        + " [options] [<results1> <results2> ... <resultsn>] ");

		for (int i = argCount; i < argv.length; i++)
			options.analysisFiles.add(argv[i]);
		MergeSummarizeAndView msv = new MergeSummarizeAndView(options);
		try {
			msv.load();
			msv.report();
		} finally {
			msv.shutdown();
		}

	}

	SortedBugCollection results;

	ArrayList<BugInstance> scaryBugs = new ArrayList<BugInstance>();

	int numLowConfidence = 0;

	int tooOld = 0;

	int harmless = 0;

	Cloud cloud;

	Cloud.Mode originalMode;

	final MSVOptions options;

	/**
	 * @param options
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws InterruptedException
	 */

	public MergeSummarizeAndView(MSVOptions options) {
		this.options = options;
	}

	public void execute() {
		try {
			load();
		} finally {
			shutdown();
		}
	}

	/**
	 * @return Returns true if there were bugs that passed all of the cutoffs.
	 */
	public int numScaryBugs() {
		return scaryBugs.size();
	}

	/**
	 * @return Returns the bugs that passed all of the cutoffs
	 */
	public Iterable<BugInstance> getScaryBugs() {
		return scaryBugs;
	}

	/**
	 * @return Returns the number of issues classified as harmless
	 */
	public int getHarmless() {
		return harmless;
	}

	/**
	 * @return Returns the number of issues that had a rank higher than the
	 *         maxRank (but not marked as harmless)
	 */
	public int getLowConfidence() {
		return numLowConfidence;
	}

	/**
	 * @return Returns the number of issues older than the age cutoff (but not
	 *         ranked higher than the maxRank or marked as harmless).
	 */
	public int getTooOld() {
		return tooOld;
	}

	private void shutdown() {
		if (cloud != null)
			cloud.shutdown();
	}

	private void load() {
		if (options.workingDirList.isEmpty()) {
			String userDir = System.getProperty("user.dir");
			if (null != userDir && !"".equals(userDir)) {
				options.workingDirList.add(userDir);
			}
		}

		IGuiCallback cliUiCallback = new CommandLineUiCallback();
		SortedBugCollection results = null;
		for (String analysisFile : options.analysisFiles) {
			try {
				SortedBugCollection more = createPreconfiguredBugCollection(options.workingDirList, options.srcDirList,
				        cliUiCallback);

				more.readXML(analysisFile);
				BugRanker.trimToMaxRank(more, options.maxConsideredRank);
				if (results != null) {
					results = union(results, more);
				} else {
					results = more;
				}
			} catch (IOException e) {
				System.err.println("Trouble reading " + analysisFile);
			} catch (DocumentException e) {
				System.err.println("Trouble parsing " + analysisFile);
			}
		}

		if (results == null) {
			throw new RuntimeException("No files successfully read");
		}

		results.setRequestDatabaseCloud(true);
		results.reinitializeCloud();
		Project project = results.getProject();
		originalMode = cloud.getMode();

		cloud.setMode(Cloud.Mode.COMMUNAL);
		MyBugReporter reporter = new MyBugReporter();
		long old = System.currentTimeMillis() - options.maxAge * 24 * 3600 * 1000L;
		for (BugInstance warning : results.getCollection())
			if (!reporter.isApplySuppressions() || !project.getSuppressionFilter().match(warning)) {
				int rank = BugRanker.findRank(warning);
				if (rank > 20)
					continue;
				if (cloud.overallClassificationIsNotAProblem(warning)) {
					harmless++;
					continue;
				}

				long firstSeen = cloud.getFirstSeen(warning);
				boolean isOld = firstSeen != 0 && firstSeen < old;
				boolean highRank = rank > options.maxRank;
				if (highRank)
					numLowConfidence++;
				else if (isOld)
					tooOld++;
				else
					scaryBugs.add(warning);
			}
	}

	private void report() {

		boolean hasScaryBugs = !scaryBugs.isEmpty();
		if (hasScaryBugs) {
			System.out.printf("%4s\n", "days");
			System.out.printf("%4s %4s %s\n", "old", "rank", "issue");
			for (BugInstance warning : scaryBugs) {
				int rank = BugRanker.findRank(warning);

				long firstSeen = cloud.getFirstSeen(warning);

				System.out.printf("%4d %4d %s\n", ageInDays(firstSeen), rank, warning.getMessageWithoutPrefix());
			}
		}

		if (numLowConfidence > 0 || tooOld > 0) {
			if (hasScaryBugs) {
				System.out.print("\nplus ");
				if (numLowConfidence > 0)
					System.out.printf("%d less scary recent issues", numLowConfidence);
				if (numLowConfidence > 0 && tooOld > 0)
					System.out.printf(" and ");
				if (tooOld > 0)
					System.out.printf("%d older issues", tooOld);
				System.out.println();
			}
		}

		if (hasScaryBugs || (options.alwaysShowGui && results.getCollection().size() > 0)) {
			if (GraphicsEnvironment.isHeadless()) {
				System.out.println("Running in GUI headless mode, can't open GUI");
				return;
			}
			GUISaveState.loadInstance();
			cloud.setMode(originalMode);
			try {
				FindBugsLayoutManagerFactory factory = new FindBugsLayoutManagerFactory(SplitLayout.class.getName());
				MainFrame.makeInstance(factory);
				MainFrame instance = MainFrame.getInstance();
				instance.waitUntilReady();

				instance.openBugCollection(results);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

	}

	static SortedBugCollection createPreconfiguredBugCollection(List<String> workingDirList, List<String> srcDirList,
	        IGuiCallback guiCallback) {
		Project project = new Project();
		for (String cwd : workingDirList) {
			project.addWorkingDir(cwd);
		}
		for (String srcDir : srcDirList) {
			project.addSourceDir(srcDir);
		}
		project.setGuiCallback(guiCallback);
		return new SortedBugCollection(project);
	}

	static int ageInDays(long firstSeen) {
		return (int) (NOW - firstSeen) / 24 / 3600 / 1000;
	}

	static final long NOW = System.currentTimeMillis();

	static class MyBugReporter extends PrintingBugReporter {

		@Override
		public void printBug(BugInstance bugInstance) {
			super.printBug(bugInstance);
		}
	}

}

// vim:ts=3