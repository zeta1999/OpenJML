package org.jmlspecs.openjml;

import static com.sun.tools.javac.parser.Tokens.TokenKind.IDENTIFIER;
import static com.sun.tools.javac.parser.Tokens.TokenKind.SEMI;
import static org.jmlspecs.openjml.JmlTokenKind.ENDJMLCOMMENT;

import java.lang.reflect.Constructor;
import java.util.function.Function;

import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.annotation.Nullable;
import org.jmlspecs.openjml.JmlTree.JmlAbstractStatement;
import org.jmlspecs.openjml.JmlTree.JmlMethodInvocation;
import org.jmlspecs.openjml.JmlTree.JmlSingleton;
import org.jmlspecs.openjml.JmlTree.JmlSource;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.JmlAttr;
import com.sun.tools.javac.parser.JmlParser;
import com.sun.tools.javac.parser.JmlScanner;
import com.sun.tools.javac.parser.JmlToken;
import com.sun.tools.javac.parser.JmlTokenizer;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.Log.WriterKind;

/** Objects of this type represents kinds of JML clauses and statements, for example,
 *  requires clauses or the \old expression. Instances represent kinds of clauses,
 *  not instances of clauses. These objects also contain behavior of the clause kinds,
 *  namely how to parse and typecheck instances of these clauses. Instances of clauses
 *  are usually instances of derived types of JmlTree.
 * @author davidcok
 *
 */
public abstract class IJmlClauseKind {
    
    public IJmlClauseKind(String keyword) {
        this.keyword = keyword;
    }
    
    // These fields and methods give behavior of JML clauses of the given kind.

    public String keyword = null;

    public String name() { return keyword; }
    
    public String toString() { return name(); }
    
    /** If true, is a method or type spec clause types in which \old without a label can be used
    */
    public boolean oldNoLabelAllowed() { return false; }
    
    /** If true, is a clause in which \pre and \old with a label can be used (e.g. asst)
     */
    public boolean preOrOldWithLabelAllowed() { return false; }
    
    public boolean preAllowed() { return false; }

    /** If true, is a method clause type in which these tokens may appear:
     *  \not_assigned \only_assigned \only_captured \only_accessible \not_modified */
    public boolean postClauseAllowed() { return false; }
    
    /** If true, is a method clause type in which the \result token may appear 
     * (and \not_assigned \only_assigned \only_captured \only_accessible \not_modified) */
    public boolean resultExpressionAllowed() { return false; }

    /** If true, is a method clause type in which the \exception token may appear 
     */
    public boolean exceptionExpressionAllowed() { return false; }

    /** If true, is a method clause type in which the \fresh token may appear 
     */
    public boolean freshExpressionAllowed() { return false; }
    
    // The following fields are initialized on demand when parsing or typechecking
    
    /** The compilation context, set when derived classes are instantiated */
    protected /*@ non_null */ Context context;
    //@ public constraint context == \old(context);

    /** The parser in use, set when derived classes are instantiated */
    protected /*@ non_null */ JmlParser parser;
    
    /** The scanner in use, set when derived classes are instantiated */
    protected /*@ non_null */ JmlScanner scanner;
    
//    /** The node factory in use, set when derived classes are instantiated */
//    protected /*@ non_null */ JmlTree.Maker jmlF;

    /** The symbol table, set when the context is set */
    protected Symtab syms;
    
//    /** The names table, set when the context is set */
//    protected Names names;
//    
//    /** The Utils instance */
//    protected Utils utils;
    
    protected Log log;
    
    /** Writes an error message to the log, using the given DiagnosticPosition
     * (typically gotten from tree.pos()), 
     * a key (as in the file org.jmlspecs.openjml.messages.properties)
     * and arguments for that key
     * @param pos the DiagnosticPosition used to identify the relevant location in the source file
     * @param key the resource key holding the error message
     * @param args (non-null) arguments for the key - there must be at least as many arguments as there are place-holders in the key string
     */
    public void error(DiagnosticPosition pos, String key, Object ... args) {
        log.error(pos,key,args);
    }
    
    /**
     * Creates an error message for which the source is a range of characters,
     * from begin up to and not including end; the identified line is that of
     * the begin position.
     */
    public void error(int begin, int end, String key, Object... args) {
        log.error(new JmlTokenizer.DiagnosticPositionSE(begin, end - 1), key,
                args); // TODO - not unicode friendly
    }

    /** Writes a warning message to the log, using the given DiagnosticPosition
     * (typically gotten from tree.pos()), a key (as in the file org.jmlspecs.openjml.messages.resources)
     * and arguments for that key
     * @param pos the DiagnosticPosition used to identify the relevant location in the source file
     * @param key the resource key holding the error message
     * @param args (non-null) arguments for the key - there must be at least as many arguments as there are place-holders in the key string
     */
    public void warning(DiagnosticPosition pos, String key, Object ... args) {
        log.warning(pos,key,args);
    }
    
    /**
     * Creates a warning message for which the source is a range of characters,
     * from begin up to and not including end; the identified line is that of
     * the begin position.
     */
    public void warning(int begin, int end, String key, Object... args) {
        log.warning(new JmlTokenizer.DiagnosticPositionSE(begin, end - 1), key,
                args); // TODO - not unicode friendly
    }

    /** Writes an informational message to the log's noticeWriter (as with
     * println).  To be used for informational or debugging information.
     * @param msg the String to write
     */
    public void info(@NonNull String msg) {
        log.getWriter(WriterKind.NOTICE).println(msg);
    }
    
    /** Sets the end position of the given tree node to be the end position of
     * the previously scanned token.
     * @param <T> the type of the node being set
     * @param tree the node whose end position is being set
     * @return returns the same node
     */
    public <T extends JCTree> T toP(T tree) {
        return parser.toP(tree);
    }

    /** Called by JmlParser when it sees the initial token for this extension.
     * The derived class implementation is responsible to scan tokens using
     * the scanner (JmlParser.getScanner()) and return a JCExpression parse
     * tree.  When called, the current scanner token is the JmlToken itself;
     * this method is responsible to scan the end of the expression (e.g. the
     * terminating parenthesis) and no more.  If an error occurs because of
     * badly formed input, the method is required to return null and to 
     * recover as best it can.  [ FIXME - return JCErroneous?]
     * <BR>Useful methods:
     * <BR>JmlParser.getScanner() - returns the current JmlScanner
     * <BR>JmlScanner.token() - the current Java token, or CUSTOM if a JML token
     * <BR>JmlScanner.jmlToken() - the current JML token (null if a Java token)
     * <BR>JmlScanner.nextToken() - scans the next token
     * <BR>JmlScanner.pos() - the current character position, used for error reporting
     * <BR>JmlParser.syntaxError(...) - to report a parsing error
     * <BR>TODO: warning and error and informational messages
     * <BR>TODO: maker(), at(), primarySuffix, toP(), arguments(), others
     * 
     * @param parser the JmlParser for this compilation unit and compilation context
     * @param typeArgs any type arguments already seen (may be null)
     * @return the AST for the expression
     */
    abstract public JCTree parse(JCModifiers mods, String keyword, IJmlClauseKind clauseType, JmlParser parser);
    
    protected void init(JmlParser parser) {
        Context c = this.context = parser.context;
//        this.names = Names.instance(c);
        this.syms = Symtab.instance(c);
//        this.utils = Utils.instance(c);
        this.log = Log.instance(c);
        this.parser = parser;
        this.scanner = parser.getScanner();
//        this.jmlF = parser.maker();
    }

    protected void wrapup(JCTree statement, IJmlClauseKind clauseType, boolean parseSemicolon) {
        wrapup(statement,clauseType,parseSemicolon, true);
    }
    
    protected void wrapup(JCTree statement, IJmlClauseKind clauseType, boolean parseSemicolon, boolean requireSemicolon) {
        parser.toP(statement);
        if (statement instanceof JmlSource) {
            ((JmlSource)statement).setSource(Log.instance(context).currentSourceFile());
        }
        //ste.line = log.currentSource().getLineNumber(pos);
        scanner.setJmlKeyword(true);
        if (!parseSemicolon) {
            // If we do not need a semicolon yet (e.g. because we already
            // parsed it or because the statement does not end with one,
            // then the scanner has already scanned the next symbol,
            // with setJmlKeyword having been (potentially) false.
            // So we need to do the following conversion.
            if (parser.token().kind == IDENTIFIER && scanner.jml()) {
                JmlTokenKind tt = JmlTokenKind.allTokens.get(scanner.chars());
                IJmlClauseKind tk = Extensions.allKinds.get(scanner.chars());
                if (tt != null) {
                    scanner.setToken(new JmlToken(tt, tk, null, parser.pos(), parser.endPos()));
                }
            }
        } else if (parser.token().ikind == ENDJMLCOMMENT) {
            // FIXME - why -2 here
            if (requireSemicolon) log.warning(parser.pos()-2, "jml.missing.semi", clauseType.name());
        } else if (parser.token().kind != SEMI) {
            error(parser.pos(), parser.endPos(), "jml.bad.construct", clauseType.name() + " statement");
            parser.skipThroughSemi();
        } else {
            parser.nextToken(); // advance to the token after the semi
        }

    }
    
    /** Derived classes implement this method to do any typechecking of the tree, which should have
     * a dynamic type corresponding to the kind of the tree; returns the type of the result, or Type.noType.
     */
    abstract public Type typecheck(JmlAttr attr, JCTree tree, Env<AttrContext> env);
    
    /** returns true if strict adherence to JML is required (language option is jml) */
    public boolean requireStrictJML() {
        return JmlOption.langJML.equals(JmlOption.value(context, JmlOption.LANG));
    }
    
    /** Issue warning if strictness is required -- e.g. call this if an extension is being used */
    public void strictCheck(JmlParser parser, JCTree e) {
        if (requireStrictJML()) {
            Log.instance(context).warning(e.pos(),"jml.not.strict",name());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////// 
    
    public static abstract class MethodClause extends IJmlClauseKind {
        public MethodClause(String keyword) { super(keyword); }
        public boolean preAllowed() { return !isPreconditionClause(); }
        public boolean isPreconditionClause() { return false; }
        @Override
        public void init(JmlParser parser) {
            super.init(parser);
            this.scanner.setJmlKeyword(false);
        }
    }
    
    public static abstract class Statement extends IJmlClauseKind{
        public Statement(String keyword) { super(keyword); }
        public boolean oldNoLabelAllowed() { return true; }
        public boolean preOrOldWithLabelAllowed() { return true; }
        public boolean preAllowed() { return true; }
        @Override
        public void init(JmlParser parser) {
            super.init(parser);
            this.scanner.setJmlKeyword(false);
        }
    }
    
    /** The kind of line annotations */
    public static abstract class LineAnnotationKind extends IJmlClauseKind {
        public LineAnnotationKind(String keyword) { super(keyword); }

        @Override
        public JCTree parse(JCModifiers mods, String keyword,
                IJmlClauseKind clauseType, JmlParser parser) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Type typecheck(JmlAttr attr, JCTree expr, Env<AttrContext> env) {
            throw new UnsupportedOperationException();
        }

        abstract public void scan(int keywordPos, String keyword, IJmlClauseKind clauseKind, JmlTokenizer tokenizer);

        /** A class that is a record of an instance of a line annotation.
         * A line annotation is captured by the scanner, not the parser,
         * and so is a bit of a different animal. */
        public static abstract class LineAnnotation {
            public int line;
            public int keywordPos;
            public IJmlClauseKind clauseKind;
            public IJmlClauseKind clauseKind() { return clauseKind; }
            abstract public java.util.List<JCExpression> exprs();
            abstract public Type typecheck(JmlAttr attr, Env<AttrContext> env);
        }
    }
    public static abstract class TypeClause extends IJmlClauseKind {
        public TypeClause(String keyword) { super(keyword); }
        @Override
        public void init(JmlParser parser) {
            super.init(parser);
            this.scanner.setJmlKeyword(false);
        }
    }

    /** A base class for JML extensions that do nont fll innto other categories */
    public static abstract class Misc extends IJmlClauseKind {
        public Misc(String keyword) { super(keyword); }
        abstract public JCTree parse(JCModifiers mods, String keyword, IJmlClauseKind clauseType, JmlParser parser);
    }
    
    /** A base class for JML extensions that are kinds of expressions */
    public static abstract class Expression extends IJmlClauseKind {
        public Expression(String keyword) { super(keyword); }
        abstract public JCExpression parse(JCModifiers mods, String keyword, IJmlClauseKind clauseType, JmlParser parser);
    }
    
    /** This class is used for JML expressions that have a standard function-call
     * form: a keyword followed by a parenthesized comma-separated list of expressions
     */
    public static abstract class FunctionLikeExpression extends Expression {
        public FunctionLikeExpression(String keyword) { super(keyword); }
        
        /** This implementation of parse() parses a keyword + 
         * parenthesized comma-separated list of expressions,
         * producing a JmlMethodInvocation node. Derived classes
         * must implement checkParse(), to do any additional checking,
         * such as that the number of arguments is correct.
         */
        public JCExpression parse(JCModifiers mods, String name, IJmlClauseKind kind, JmlParser parser) {
            init(parser);
            int startx = parser.pos();
            JmlTokenKind jt = parser.jmlTokenKind();
            parser.nextToken();
            if (parser.token().kind != TokenKind.LPAREN) {
                return parser.syntaxError(startx, null, "jml.args.required", jt.internedName());
//            } else if (typeArgs != null && !typeArgs.isEmpty()) {
//                return parser.syntaxError(p, null, "jml.no.typeargs.allowed", jt.internedName());
            } else {
                int preferredPos = parser.pos(); // points at the left-paren
                List<JCExpression> args = parser.arguments();
                JmlMethodInvocation t = toP(parser.maker().at(preferredPos).JmlMethodInvocation(this, args));
                t.startpos = startx;
                t.token = jt; // FIXME - replace using jt with a kind
                checkParse(parser,t);
                return parser.primarySuffix(t, null);
            }
        }
        
        abstract public void checkParse(JmlParser parser, JmlMethodInvocation e);

        /** A helper method that can be called in a derived class's implementation
         * of checkParse() -- this method applies the given function to the number of
         * arguments and emits an error message if the function returns false.
         */
        public void checkNumberArgs(JmlParser parser, JmlMethodInvocation e, Function<Integer,Boolean> f, String key, Object ... messageArgs) {
            if (!f.apply(e.args.size())) {
                error(e.pos, parser.getEndPos(e), key, messageArgs);
            }
        }

        /** A helper method that can be called in a derived class's implementation
         * of checkParse() -- for the case of exactly one argument.
         */
        public void checkOneArg(JmlParser parser, JmlMethodInvocation e) {
            checkNumberArgs(parser, e, (n)->(n==1), "jml.one.arg", e.kind.name());
        }
        
        public void typecheckHelper(Attr attr, List<JCExpression> args, Env<AttrContext> localEnv) {
            ListBuffer<Type> argTypes = new ListBuffer<>();
            Attr.ResultInfo resultInfo = attr.new ResultInfo(Kinds.VAL, Type.noType );
            for (JCExpression e: args) {
                Type t = attr.attribExpr(e, localEnv);
                t = attr.check(e, t, Kinds.VAL, resultInfo );
                argTypes.add(t);
            }
        }
    }
    
    /** This class is used for JML items that are just a keyword but
     * are not expressions or other categories themselves.
     */
    public static abstract class Singleton extends IJmlClauseKind.Misc {
        
        public Singleton(String name) { super(name); }
        
        @Override
        public JCTree parse(JCModifiers mods, String keyword, IJmlClauseKind clauseType, JmlParser parser) {
            init(parser);
            IJmlClauseKind jt = parser.jmlTokenClauseKind();
            int p = parser.pos();
            String stringRep = parser.getScanner().chars();
            parser.nextToken();
            if (parser.token().kind == TokenKind.LPAREN) {
                return parser.syntaxError(p, null, "jml.no.args.allowed", jt.name());
            } else {
                JmlSingleton e = parser.maker().at(p).JmlSingleton(jt);
                e.kind = this;
                checkParse(parser,e,stringRep);
                return e;
            }
        }
        
        /** A method meant to be overridden in derived classes to do any
         * additional checking -- typically none for a singleton.
         */
        public void checkParse(JmlParser parser, JmlSingleton e, String rep) {}

    }
    /** This class is used for JML expressions that are just a keyword with
     * no parenthesized argument list
     */
    public static abstract class SingletonExpression extends Expression {
        
        public SingletonExpression(String name) { super(name); }
        
        @Override
        public JCExpression parse(JCModifiers mods, String keyword, IJmlClauseKind clauseType, JmlParser parser) {
            init(parser);
            IJmlClauseKind jt = parser.jmlTokenClauseKind();
            int p = parser.pos();
            String stringRep = parser.getScanner().chars();
            parser.nextToken();
            if (parser.token().kind == TokenKind.LPAREN) {
                return parser.syntaxError(p, null, "jml.no.args.allowed", jt.name());
            } else {
                JmlSingleton e = parser.maker().at(p).JmlSingleton(jt);
                e.kind = this;
                checkParse(parser,e,stringRep);
                return e;
            }
        }
        
        /** A method meant to be overridden in derived classes to do any
         * additional checking -- typically none for a singleton.
         */
        public void checkParse(JmlParser parser, JmlSingleton e, String rep) {}

    }
    
    public static abstract class Modifier extends IJmlClauseKind {
        public Modifier(String keyword) { super(keyword); }
    }


}
