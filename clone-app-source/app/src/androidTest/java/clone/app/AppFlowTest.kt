package clone.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Cases F01-H1, F01-E3, F01-E6, F04-H3 and the touch-target rule from test-plan.md. Written, not run. */
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun createNote(title: String) {
        rule.onNodeWithText("+ New").performClick()
        rule.onNodeWithText("New note").performClick()
        if (title.isNotEmpty()) rule.onNode(hasSetTextAction()).performTextInput(title)
        rule.onNodeWithText("Create").performClick()
    }

    @Test fun libraryShowsTitleAndNewTile() {          // F01-H0
        rule.onNodeWithText("My notes").assertIsDisplayed()
        rule.onNodeWithText("+ New").assertIsDisplayed()
    }

    @Test fun creatingANoteOpensTheEditorWithNothingToUndo() {   // F01-H1
        createNote("Test note")
        rule.onNodeWithText("Test note").assertIsDisplayed()
        rule.onNodeWithText("Undo").assertIsNotEnabled()
        rule.onNodeWithText("Redo").assertIsNotEnabled()
    }

    @Test fun anEmptyTitleBecomesUntitledNote() {      // F01-E1
        createNote("")
        rule.onNodeWithText("Untitled note").assertIsDisplayed()
    }

    @Test fun cyrillicEmojiAndAccentedTitlesAreShownAsTyped() {   // F01-E3
        createNote("Конспект 😀 café")
        rule.onNodeWithText("Конспект 😀 café").assertIsDisplayed()
    }

    @Test fun theEraserCanBeSelected() {               // F04-H3
        createNote("Eraser test")
        rule.onNodeWithContentDescription("Eraser").performClick().assertIsSelected()
    }

    @Test fun toolButtonsMeetTheTouchTargetRule() {    // a11y
        createNote("Targets")
        rule.onNodeWithContentDescription("Pen").assertTouchHeightIsAtLeast(48.dp).assertTouchWidthIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Eraser").assertTouchHeightIsAtLeast(48.dp).assertTouchWidthIsAtLeast(48.dp)
    }
}

/** Folder cases F01-F1 to F01-F3 from test-plan.md. Written, not run. */
class FolderFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun newFolder(name: String) {
        rule.onNodeWithText("New folder").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(name)
        rule.onNodeWithText("Create").performClick()
    }

    @Test fun aNewFolderAppearsAsATile() {
        newFolder("Maths")
        rule.onNodeWithText("Maths").assertIsDisplayed()
    }

    @Test fun aSecondFolderWithTheSameNameGivesAMessage() {
        newFolder("Dup")
        newFolder("dup")
        rule.onNodeWithText("A folder with that name already exists here").assertIsDisplayed()
    }

    @Test fun openingAFolderShowsItsBreadcrumb() {
        newFolder("Biology")
        rule.onNodeWithText("Biology").performClick()
        rule.onNodeWithText("My notes").assertIsDisplayed()
        rule.onNodeWithText("Nothing here yet. Use the + tile to start a note, or New folder to organize.").assertIsDisplayed()
    }
}
