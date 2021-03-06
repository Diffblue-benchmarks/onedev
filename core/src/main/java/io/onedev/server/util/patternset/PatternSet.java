package io.onedev.server.util.patternset;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.unbescape.java.JavaEscape;

import io.onedev.commons.utils.stringmatch.Matcher;
import io.onedev.commons.utils.stringmatch.WildcardPathMatcher;
import io.onedev.server.exception.OneException;
import io.onedev.server.util.patternset.PatternSetParser.PatternContext;
import io.onedev.server.util.patternset.PatternSetParser.PatternsContext;

public class PatternSet implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String ESCAPE_CHARS = "\\\"";
	
	private final Set<String> includes;
	
	private final Set<String> excludes;
	
	public PatternSet(Set<String> includes, Set<String> excludes) {
		this.includes = includes;
		this.excludes = excludes;
	}

	public Set<String> getIncludes() {
		return includes;
	}

	public Set<String> getExcludes() {
		return excludes;
	}

	public boolean matches(Matcher matcher, String value) {
		for (String exclude: excludes) {
			if (matcher.matches(exclude, value))
				return false;
		}
		for (String include: includes) {
			if (matcher.matches(include, value))
				return true;
		}
		return false;
	}
	
	public Collection<File> listFiles(File dir) {
		Collection<File> files = new ArrayList<>();
		WildcardPathMatcher matcher = new WildcardPathMatcher();
		int baseLen = dir.getAbsolutePath().length() + 1;
		for (File file: FileUtils.listFiles(dir, null, true)) {
			String relative = file.getAbsolutePath().substring(baseLen);
			relative = relative.replace('\\', '/');
			if (matches(matcher, relative))
				files.add(file);
		}
		return files;
	}
	
	public static PatternSet fromString(String patternSetString) {
		Set<String> includes = new HashSet<>();
		Set<String> excludes = new HashSet<>();
		
		if (patternSetString != null) {
			PatternsContext patterns = parse(patternSetString);
			
			for (PatternContext pattern: patterns.pattern()) {
				String value;
				if (pattern.Quoted() != null)
					value = unescape(pattern.Quoted().getText());
				else
					value = pattern.NQuoted().getText();
				if (pattern.Excluded() != null) {
					excludes.add(value);
				} else {
					includes.add(value);
				}
			}			
		}
		
		return new PatternSet(includes, excludes);
	}
	
	public static String unescape(String quoted) {
		return JavaEscape.unescapeJava(quoted.substring(1, quoted.length()-1));
	}

	public static PatternsContext parse(String patternSetString) {
		CharStream is = CharStreams.fromString(patternSetString); 
		PatternSetLexer lexer = new PatternSetLexer(is);
		lexer.removeErrorListeners();
		lexer.addErrorListener(new BaseErrorListener() {

			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new OneException("Malformed patterns");
			}
			
		});
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PatternSetParser parser = new PatternSetParser(tokens);
		parser.removeErrorListeners();
		parser.setErrorHandler(new BailErrorStrategy());
		return parser.patterns();
	}

	public static String quoteIfNecessary(String pattern) {
		if (StringUtils.containsAny(pattern, " \"") || pattern.startsWith("-"))
			return "\"" + escape(pattern) + "\"";
		else
			return pattern;
	}
	
	public static String escape(String pattern) {
		StringBuilder builder = new StringBuilder();
		for (int i=0; i<pattern.length(); i++) {
			char ch = pattern.charAt(i);
			if (ESCAPE_CHARS.indexOf(ch) != -1)
				builder.append("\\");
			builder.append(ch);
		}
		return builder.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String include: getIncludes())
			builder.append(include).append(" ");
		for (String exclude: getExcludes())
			builder.append("-").append(exclude).append(" ");
		return builder.toString().trim();
	}
	
}