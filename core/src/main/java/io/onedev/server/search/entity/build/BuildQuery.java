package io.onedev.server.search.entity.build;

import static io.onedev.server.util.BuildConstants.FIELD_BUILD_DATE;
import static io.onedev.server.util.BuildConstants.FIELD_COMMIT;
import static io.onedev.server.util.BuildConstants.FIELD_CONFIGURATION;
import static io.onedev.server.util.BuildConstants.FIELD_VERSION;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import io.onedev.server.exception.OneException;
import io.onedev.server.model.Build;
import io.onedev.server.model.Project;
import io.onedev.server.search.entity.AndCriteriaHelper;
import io.onedev.server.search.entity.EntityCriteria;
import io.onedev.server.search.entity.EntityQuery;
import io.onedev.server.search.entity.EntitySort;
import io.onedev.server.search.entity.EntitySort.Direction;
import io.onedev.server.search.entity.NotCriteriaHelper;
import io.onedev.server.search.entity.OrCriteriaHelper;
import io.onedev.server.search.entity.build.BuildQueryParser.AndCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.CriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.FieldOperatorValueCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.NotCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.OperatorCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.OperatorValueCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.OrCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.OrderContext;
import io.onedev.server.search.entity.build.BuildQueryParser.ParensCriteriaContext;
import io.onedev.server.search.entity.build.BuildQueryParser.QueryContext;
import io.onedev.server.util.BuildConstants;

public class BuildQuery extends EntityQuery<Build> {

	private static final long serialVersionUID = 1L;

	private final EntityCriteria<Build> criteria;
	
	private final List<EntitySort> sorts;
	
	public BuildQuery(@Nullable EntityCriteria<Build> criteria, List<EntitySort> sorts) {
		this.criteria = criteria;
		this.sorts = sorts;
	}

	public BuildQuery() {
		this(null, new ArrayList<>());
	}
	
	public static BuildQuery parse(Project project, @Nullable String queryString, boolean validate) {
		if (queryString != null) {
			CharStream is = CharStreams.fromString(queryString); 
			BuildQueryLexer lexer = new BuildQueryLexer(is);
			lexer.removeErrorListeners();
			lexer.addErrorListener(new BaseErrorListener() {

				@Override
				public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
						int charPositionInLine, String msg, RecognitionException e) {
					throw new OneException("Malformed query syntax", e);
				}
				
			});
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			BuildQueryParser parser = new BuildQueryParser(tokens);
			parser.removeErrorListeners();
			parser.setErrorHandler(new BailErrorStrategy());
			QueryContext queryContext;
			try {
				queryContext = parser.query();
			} catch (Exception e) {
				if (e instanceof OneException)
					throw e;
				else
					throw new OneException("Malformed query syntax", e);
			}
			CriteriaContext criteriaContext = queryContext.criteria();
			EntityCriteria<Build> buildCriteria;
			if (criteriaContext != null) {
				buildCriteria = new BuildQueryBaseVisitor<EntityCriteria<Build>>() {

					@Override
					public EntityCriteria<Build> visitOperatorCriteria(OperatorCriteriaContext ctx) {
						switch (ctx.operator.getType()) {
						case BuildQueryLexer.Successful:
							return new SuccessfulCriteria();
						case BuildQueryLexer.Failed:
							return new FailedCriteria();
						case BuildQueryLexer.InError:
							return new InErrorCriteria();
						case BuildQueryLexer.Running:
							return new RunningCriteria();
						default:
							throw new OneException("Unexpected operator: " + ctx.operator.getText());
						}
					}
					
					@Override
					public EntityCriteria<Build> visitOperatorValueCriteria(OperatorValueCriteriaContext ctx) {
						String value = getValue(ctx.Quoted().getText());
						return new FixedIssueCriteria(getIssue(project, value));
					}
					
					@Override
					public EntityCriteria<Build> visitParensCriteria(ParensCriteriaContext ctx) {
						return visit(ctx.criteria());
					}

					@Override
					public EntityCriteria<Build> visitFieldOperatorValueCriteria(FieldOperatorValueCriteriaContext ctx) {
						String fieldName = getValue(ctx.Quoted(0).getText());
						String value = getValue(ctx.Quoted(1).getText());
						int operator = ctx.operator.getType();
						if (validate)
							checkField(project, fieldName, operator);
						
						switch (operator) {
						case BuildQueryLexer.IsBefore:
						case BuildQueryLexer.IsAfter:
							Date dateValue = getDateValue(value);
							if (fieldName.equals(FIELD_BUILD_DATE))
								return new BuildDateCriteria(dateValue, value, operator);
							else
								throw new IllegalStateException();
						case BuildQueryLexer.Matches:
							if (fieldName.equals(FIELD_VERSION))
								return new VersionCriteria(value);
							else
								throw new IllegalStateException();
						case BuildQueryLexer.Is:
							switch (fieldName) {
							case FIELD_COMMIT:
								return new CommitCriteria(getCommitId(project, value));
							case FIELD_CONFIGURATION:
								return new ConfigurationCriteria(getConfiguration(project, value));
							default: 
								throw new IllegalStateException();
							}
						default:
							throw new IllegalStateException();
						}
					}
					
					@Override
					public EntityCriteria<Build> visitOrCriteria(OrCriteriaContext ctx) {
						List<EntityCriteria<Build>> childCriterias = new ArrayList<>();
						for (CriteriaContext childCtx: ctx.criteria())
							childCriterias.add(visit(childCtx));
						return new OrCriteriaHelper<Build>(childCriterias);
					}

					@Override
					public EntityCriteria<Build> visitAndCriteria(AndCriteriaContext ctx) {
						List<EntityCriteria<Build>> childCriterias = new ArrayList<>();
						for (CriteriaContext childCtx: ctx.criteria())
							childCriterias.add(visit(childCtx));
						return new AndCriteriaHelper<Build>(childCriterias);
					}

					@Override
					public EntityCriteria<Build> visitNotCriteria(NotCriteriaContext ctx) {
						return new NotCriteriaHelper<Build>(visit(ctx.criteria()));
					}

				}.visit(criteriaContext);
			} else {
				buildCriteria = null;
			}

			List<EntitySort> buildSorts = new ArrayList<>();
			for (OrderContext order: queryContext.order()) {
				String fieldName = getValue(order.Quoted().getText());
				if (validate && !BuildConstants.ORDER_FIELDS.containsKey(fieldName)) 
					throw new OneException("Can not order by field: " + fieldName);
				
				EntitySort commentSort = new EntitySort();
				commentSort.setField(fieldName);
				if (order.direction != null && order.direction.getText().equals("asc"))
					commentSort.setDirection(Direction.ASCENDING);
				else
					commentSort.setDirection(Direction.DESCENDING);
				buildSorts.add(commentSort);
			}
			
			return new BuildQuery(buildCriteria, buildSorts);
		} else {
			return new BuildQuery();
		}
	}
	
	public static void checkField(Project project, String fieldName, int operator) {
		if (!BuildConstants.QUERY_FIELDS.contains(fieldName))
			throw new OneException("Field not found: " + fieldName);
		switch (operator) {
		case BuildQueryLexer.IsBefore:
		case BuildQueryLexer.IsAfter:
			if (!fieldName.equals(BuildConstants.FIELD_BUILD_DATE)) 
				throw newOperatorException(fieldName, operator);
			break;
		case BuildQueryLexer.Matches:
			if (!fieldName.equals(FIELD_VERSION))
				throw newOperatorException(fieldName, operator);
			break;
		case BuildQueryLexer.Is:
			if (!fieldName.equals(FIELD_COMMIT) && !fieldName.equals(FIELD_CONFIGURATION)) 
				throw newOperatorException(fieldName, operator);
			break;
		}
	}
	
	private static OneException newOperatorException(String fieldName, int operator) {
		return new OneException("Field '" + fieldName + "' is not applicable for operator '" + getRuleName(operator) + "'");
	}
	
	public static String getRuleName(int rule) {
		return getLexerRuleName(BuildQueryLexer.ruleNames, rule);
	}
	
	public static int getOperator(String operatorName) {
		return getLexerRule(BuildQueryLexer.ruleNames, operatorName);
	}
	
	@Override
	public EntityCriteria<Build> getCriteria() {
		return criteria;
	}

	@Override
	public List<EntitySort> getSorts() {
		return sorts;
	}
	
	public static BuildQuery merge(BuildQuery query1, BuildQuery query2) {
		List<EntityCriteria<Build>> criterias = new ArrayList<>();
		if (query1.getCriteria() != null)
			criterias.add(query1.getCriteria());
		if (query2.getCriteria() != null)
			criterias.add(query2.getCriteria());
		List<EntitySort> sorts = new ArrayList<>();
		sorts.addAll(query1.getSorts());
		sorts.addAll(query2.getSorts());

		if (criterias.size() > 1)
			return new BuildQuery(new AndCriteriaHelper<Build>(criterias), sorts);
		else if (criterias.size() == 1)
			return new BuildQuery(criterias.iterator().next(), sorts);
		else
			return new BuildQuery(null, sorts);
	}
}
