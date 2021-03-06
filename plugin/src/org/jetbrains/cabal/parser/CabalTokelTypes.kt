package org.jetbrains.cabal.parser

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.haskell.parser.CabalCompositeElementType
import org.jetbrains.haskell.parser.HaskellToken
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.extapi.psi.ASTWrapperPsiElement
import org.jetbrains.cabal.psi.*

public trait CabalTokelTypes {


    class object {
        val defaultContructor : (ASTNode) -> PsiElement = { node ->
            ASTWrapperPsiElement(node)
        }
        val COLON: IElementType = HaskellToken(":")
        val COMMA: IElementType = HaskellToken(",")
        val COMMENT: IElementType = HaskellToken("COMMENT")
        val LEFT_PAREN : IElementType = HaskellToken("(")
        val RIGHT_PAREN : IElementType = HaskellToken(")")
        val DOT: IElementType = HaskellToken(".")
        val END_OF_LINE_COMMENT: IElementType? = HaskellToken("--")
        val STRING: IElementType = HaskellToken("string")
        val NUMBER: IElementType = HaskellToken("number")
        val ID: IElementType = HaskellToken("id")

        val PROPERTY: IElementType = CabalCompositeElementType("PROPERTY", defaultContructor)
        val NAME: IElementType = CabalCompositeElementType("NAME", { Name(it) })
        val PROPERTY_KEY: IElementType = CabalCompositeElementType("PROPERTY_KEY", { PropertyKey(it) })
        val PROPERTY_VALUE: IElementType = CabalCompositeElementType("PROPERTY_VALUE" , defaultContructor)
        val EXECUTABLE: IElementType = CabalCompositeElementType("EXECUTABLE", { Executable(it) })
        val TEST_SUITE: IElementType = CabalCompositeElementType("TEST_SUITE", { TestSuite(it) })
        val SECTION: IElementType = CabalCompositeElementType("SECTION", defaultContructor)
        val SECTION_TYPE: IElementType = CabalCompositeElementType("SECTION_TYPE", { SectionType(it) })
        val COMMENTS: TokenSet = TokenSet.create(END_OF_LINE_COMMENT, COMMENT)
        val WHITESPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    }
}
