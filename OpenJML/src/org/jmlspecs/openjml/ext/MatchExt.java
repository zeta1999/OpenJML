package org.jmlspecs.openjml.ext;

import org.jmlspecs.annotation.Nullable;
import org.jmlspecs.openjml.IJmlClauseKind;
import org.jmlspecs.openjml.JmlTokenKind;
import org.jmlspecs.openjml.JmlTree.JmlExpression;
import org.jmlspecs.openjml.JmlTree.JmlMatchExpression;
import org.jmlspecs.openjml.JmlTree.JmlMethodInvocation;

import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.JmlAttr;
import com.sun.tools.javac.parser.JmlParser;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class MatchExt extends ExpressionExtension {

    public MatchExt(Context context) {
        super(context);
    }

    static public JmlTokenKind[] tokens() { return new JmlTokenKind[]{
            JmlTokenKind.MATCH }; }
    
    @Override
    public IJmlClauseKind[]  clauseTypesA() { return clauseTypes(); }
    public static IJmlClauseKind[] clauseTypes() {
        return null;
    }
    
    @Override
    public JCExpression parse(String keyword, IJmlClauseKind clauseType, JmlParser parser) {
        this.parser = parser;
        int p = parser.pos();
        parser.nextToken();
        JCExpression expr = parser.parseExpression();
        ListBuffer<JmlMatchExpression.MatchCase> cases = new ListBuffer<>();
        parser.accept(TokenKind.LBRACE);
        while (parser.token().kind == TokenKind.CASE) {
            parser.accept(TokenKind.CASE);
            // Can't just parse an expression, because then the -> looks like part of a lambda expression
            // Must start with an identifier
            boolean saved = parser.underscoreOK;
            parser.underscoreOK = true;
            JCExpression id = toP(jmlF.at(parser.token().pos).Ident(parser.ident())); // FIXME -  - is the position OK
            JCExpression caseExpression = parser.primarySuffix(id,List.<JCExpression>nil());
            parser.underscoreOK = saved;
            parser.accept(TokenKind.ARROW);
            JCExpression value = parser.parseExpression();
            parser.accept(TokenKind.SEMI);
            cases.add(new JmlMatchExpression.MatchCase(caseExpression,value));
        }
        parser.accept(TokenKind.RBRACE);
        return jmlF.at(p).JmlMatchExpression(expr,cases.toList());
        // FIXME - the above needs better error messages and recovery
    }
    
    @Override
    public JCExpression parse(JmlParser parser,
            @Nullable List<JCExpression> typeArgs) {
        return parse(null,null,parser);
    }

    
    static public class MatchExpr extends JmlExpression {

        @Override
        public Kind getKind() {
            return null;
        }

        @Override
        public Tag getTag() {
            return null;
        }

        @Override
        public void accept(Visitor v) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            // TODO Auto-generated method stub
            return null;
        }
        
    }

    @Override
    public void checkParse(JmlParser parser, JmlMethodInvocation e) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Type typecheck(JmlAttr attr, JCExpression expr,
            Env<AttrContext> env) {
        // TODO Auto-generated method stub
        return null;
    }

}
