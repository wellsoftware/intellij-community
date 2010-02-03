/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyExpectedTypesProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.codeInsight.GroovyExpectedTypesProvider");

  private static final ExpectedTypesProvider.ExpectedClassProvider OUR_GLOBAL_SCOPE_CLASS_PROVIDER = new ExpectedTypesProvider.ExpectedClassProvider() {
    public PsiField[] findDeclaredFields(final PsiManager manager, String name) {
      final PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getFieldsByName(name, scope);
    }

    public PsiMethod[] findDeclaredMethods(final PsiManager manager, String name) {
      final PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getMethodsByName(name, scope);
    }
  };

  public GroovyExpectedTypesProvider() {
  }

  public static GroovyExpectedTypesProvider getInstance(Project project) {
    return ServiceManager.getService(project, GroovyExpectedTypesProvider.class);
  }

  private static ExpectedTypeInfoImpl createInfoImpl(@NotNull PsiType type, int kind, PsiType defaultType, TailType tailType) {
    int dims = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
      LOG.assertTrue(defaultType instanceof PsiArrayType);
      defaultType = ((PsiArrayType)defaultType).getComponentType();
      dims++;
    }
    return new ExpectedTypeInfoImpl(type, kind, dims, defaultType, tailType);
  }


  @Nullable
  public ExpectedTypeInfo[] getExpectedTypes(GrExpression expr,
                                             boolean forCompletion,
                                             final boolean voidable) {
    return getExpectedTypes(expr, forCompletion, OUR_GLOBAL_SCOPE_CLASS_PROVIDER, voidable);
  }

  @Nullable
  public ExpectedTypeInfo[] getExpectedTypes(GrExpression expr,
                                             boolean forCompletion,
                                             ExpectedTypesProvider.ExpectedClassProvider classProvider,
                                             final boolean voidable) {
    if (expr == null) return null;
    PsiElement parent = expr.getParent();
    while (parent instanceof GrParenthesizedExpression) {
      expr = (GrExpression)parent;
      parent = parent.getParent();
    }
    MyParentVisitor visitor = new MyParentVisitor(expr, forCompletion, classProvider, voidable);
    ((GroovyPsiElement)parent).accept(visitor);
    return visitor.getResult();
  }


  private static class MyParentVisitor extends GroovyElementVisitor {
    private ExpectedTypeInfo[] myResult = ExpectedTypeInfo.EMPTY_ARRAY;

    private GrExpression myExpr;
    private final boolean myForCompletion;
    private final ExpectedTypesProvider.ExpectedClassProvider myClassProvider;
    private final boolean myVoidable;

    private MyParentVisitor(GrExpression expr,
                            boolean forCompletion,
                            ExpectedTypesProvider.ExpectedClassProvider classProvider,
                            boolean voidable) {
      myExpr = expr;
      myForCompletion = forCompletion;
      myClassProvider = classProvider;
      myVoidable = voidable;
    }

    public ExpectedTypeInfo[] getResult() {
      return myResult;
    }

    @Override
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
      if (myExpr == assignment.getRValue()) {
        GrExpression lExpr = assignment.getLValue();
        PsiType type = lExpr.getType();
        if (type != null) {
          TailType tailType = getAssignmentRValueTailType(assignment);
          ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tailType);
          if (lExpr instanceof GrReferenceExpression) {
            PsiElement refElement = ((GrReferenceExpression)lExpr).resolve();
            if (refElement instanceof GrVariable) {
              info.expectedName = getPropertyName((GrVariable)refElement);
            }
          }
          myResult = new ExpectedTypeInfo[]{info};
        }
        else {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }

      }
      else {
        if (myForCompletion) {
          myExpr = (GrExpression)myExpr.getParent();
          ((GroovyPsiElement)assignment.getParent()).accept(this);
          return;
        }

        GrExpression rExpr = assignment.getRValue();
        if (rExpr != null) {
          PsiType type = rExpr.getType();
          if (type != null) {
            if (type instanceof PsiClassType) {
              final PsiClass resolved = ((PsiClassType)type).resolve();
              if (resolved instanceof PsiAnonymousClass) {
                type = ((PsiAnonymousClass)resolved).getBaseClassType();
              }
            }
            ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, type,
                                                       TailType.NONE);
            myResult = new ExpectedTypeInfo[]{info};
            return;
          }
        }
        myResult = ExpectedTypeInfo.EMPTY_ARRAY;
      }
    }

    @Override
    public void visitArgumentList(GrArgumentList list) {
      final PsiElement parent = list.getParent();

      GroovyResolveResult[] results = null;
      if (parent instanceof GrMethodCallExpression) {
        final GrExpression expression = ((GrMethodCallExpression)parent).getInvokedExpression();
        if (expression instanceof GrReferenceExpression) {
          results = ((GrReferenceExpression)expression).multiResolve(false);
        }
      }
      else if (parent instanceof GrApplicationStatement) {
        final GrExpression expression = ((GrApplicationStatement)parent).getFunExpression();
        if (expression instanceof GrReferenceExpression) {
          results = ((GrReferenceExpression)expression).multiResolve(false);
        }
      }
      else if (parent instanceof GrConstructorCall) {
        results = ((GrConstructorCall)parent).multiResolveConstructor();
      }

      if (results == null || results.length == 0) return;

      final int argumentIndex = list.getExpressionArgumentIndex(myExpr);
      assert argumentIndex != -1;

      Set<ExpectedTypeInfo> infos = new HashSet<ExpectedTypeInfo>();
      for (GroovyResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (!(element instanceof PsiMethod)) continue;
        final PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
        if (parameters.length < argumentIndex) continue;
        final PsiParameter parameter = parameters[argumentIndex];
        final PsiType type = parameter.getType();
        final TailType tailType = getMethodArgumentTailType(myExpr, argumentIndex, (PsiMethod)element, result.getSubstitutor(), parameters);
        infos.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tailType));
      }
      myResult = infos.toArray(new ExpectedTypeInfo[infos.size()]);
    }

    private static TailType getAssignmentRValueTailType(GrAssignmentExpression assignment) {
      if (assignment.getParent() instanceof GrExpression) {
        if (!(assignment.getParent().getParent() instanceof GrForStatement)) {
          return TailType.SEMICOLON;
        }

        GrForStatement forStatement = (GrForStatement)assignment.getParent().getParent();
        final GrForClause forClause = forStatement.getClause();
        if (forClause instanceof GrTraditionalForClause) {
          if (ArrayUtil.find(((GrTraditionalForClause)forClause).getUpdate(), assignment.getParent()) != -1) {
            return TailType.SEMICOLON;
          }
        }
      }
      return TailType.NONE;
    }

    @Nullable
    private static String getPropertyName(GrVariable variable) {
      final String name = variable.getName();
      if (name == null) return null;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      VariableKind variableKind = codeStyleManager.getVariableKind(variable);
      return codeStyleManager.variableNameToPropertyName(name, variableKind);
    }

    private TailType getMethodArgumentTailType(final GrExpression argument, final int index, final PsiMethod method, final PsiSubstitutor substitutor,
                                               final PsiParameter[] parms) {
      if (index >= parms.length) {
        return TailType.NONE;
      }
      if (index == parms.length - 1) {
        //myTailType = CompletionUtil.NONE_TAIL;
        final PsiElement call = argument.getParent().getParent();

        PsiType returnType = method.getReturnType();
        if (returnType != null) returnType = substitutor.substitute(returnType);
        return (PsiType.VOID.equals(returnType) || returnType == null) && call.getParent() instanceof PsiStatement
               ? TailTypes.CALL_RPARENTH_SEMICOLON
               : TailTypes.CALL_RPARENTH;
      }
      return TailType.COMMA;
    }
  }
}
