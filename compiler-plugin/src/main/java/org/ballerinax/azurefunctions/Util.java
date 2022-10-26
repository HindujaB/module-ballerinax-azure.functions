/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinax.azurefunctions;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticProperty;
import io.ballerina.tools.diagnostics.Location;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextRange;

import java.util.List;
import java.util.Optional;

/**
 * Contains the utilities required for the compiler extension.
 *
 * @since 2.0.0
 */
public class Util {

    public static Optional<String> extractValueFromAnnotationField(SpecificFieldNode fieldNode) {
        Optional<ExpressionNode> expressionNode = fieldNode.valueExpr();
        if (expressionNode.isEmpty()) {
            return Optional.empty();
        }
        ExpressionNode expressionNode1 = expressionNode.get();
        if (expressionNode1.kind() == SyntaxKind.STRING_LITERAL) {
            String text1 = ((BasicLiteralNode) expressionNode1).literalToken().text();
            return Optional.of(text1.substring(1, text1.length() - 1));
        } else if (expressionNode1.kind() == SyntaxKind.DECIMAL_INTEGER_LITERAL_TOKEN) {
            String text1 = ((BasicLiteralNode) expressionNode1).literalToken().text();
            return Optional.of(text1);
        }
        return Optional.empty();
    }

    /**
     * Find node of this symbol.
     *
     * @param symbol {@link Symbol}
     * @return {@link NonTerminalNode}
     */
    public static NonTerminalNode findNode(ServiceDeclarationNode serviceDeclarationNode, Symbol symbol) {
        if (symbol.getLocation().isEmpty()) {
            return null;
        }
        SyntaxTree syntaxTree = serviceDeclarationNode.syntaxTree();
        TextDocument textDocument = syntaxTree.textDocument();
        LineRange symbolRange = symbol.getLocation().get().lineRange();
        int start = textDocument.textPositionFrom(symbolRange.startLine());
        int end = textDocument.textPositionFrom(symbolRange.endLine());
        return ((ModulePartNode) syntaxTree.rootNode()).findNode(TextRange.from(start, end - start), true);
    }

    public static String resourcePathToString(NodeList<Node> nodes) {
        StringBuilder out = new StringBuilder();
        for (Node node : nodes) {
            if (node.kind() == SyntaxKind.STRING_LITERAL) {
                String value = ((BasicLiteralNode) node).literalToken().text();
                out.append(value, 1, value.length() - 1);
            } else if (node.kind() == SyntaxKind.SLASH_TOKEN) {
                Token token = (Token) node;
                out.append(token.text());
            } else if (node.kind() == SyntaxKind.IDENTIFIER_TOKEN) {
                out.append(((IdentifierToken) node).text());
            }
        }
        String finalPath = out.toString();
        if (finalPath.startsWith("/")) {
            return finalPath.substring(1);
        }
        return finalPath;
    }

    public static void updateDiagnostic(SyntaxNodeAnalysisContext ctx, Location location,
                                        AzureDiagnosticCodes httpDiagnosticCodes) {
        DiagnosticInfo diagnosticInfo = getDiagnosticInfo(httpDiagnosticCodes);
        ctx.reportDiagnostic(DiagnosticFactory.createDiagnostic(diagnosticInfo, location));
    }

    public static void updateDiagnostic(SyntaxNodeAnalysisContext ctx, Location location,
                                        AzureDiagnosticCodes azureDiagnosticCodes, Object... argName) {
        DiagnosticInfo diagnosticInfo = getDiagnosticInfo(azureDiagnosticCodes, argName);
        ctx.reportDiagnostic(DiagnosticFactory.createDiagnostic(diagnosticInfo, location));
    }

    public static void updateDiagnostic(SyntaxNodeAnalysisContext ctx, Location location,
                                        AzureDiagnosticCodes azureDiagnosticCodes,
                                        List<DiagnosticProperty<?>> diagnosticProperties, String argName) {
        DiagnosticInfo diagnosticInfo = getDiagnosticInfo(azureDiagnosticCodes, argName);
        ctx.reportDiagnostic(DiagnosticFactory.createDiagnostic(diagnosticInfo, location, diagnosticProperties));
    }

    public static DiagnosticInfo getDiagnosticInfo(AzureDiagnosticCodes diagnostic, Object... args) {
        return new DiagnosticInfo(diagnostic.getCode(), String.format(diagnostic.getMessage(), args),
                diagnostic.getSeverity());
    }

    public static boolean isSymbolAzureFunctions(Symbol definition) {
        Optional<ModuleSymbol> definitionModule = definition.getModule();
        if (definitionModule.isEmpty()) {
            return false;
        }
        ModuleID moduleID = definitionModule.get().id();
        return moduleID.orgName().equals(Constants.AZURE_FUNCTIONS_PACKAGE_ORG) &&
                moduleID.packageName().equals(Constants.AZURE_FUNCTIONS_MODULE_NAME);
    }
}