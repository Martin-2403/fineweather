package com.example.fineweather.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val augsburg = GeoPlace("1", "Augsburg", "Germany", "Bavaria", 48.37, 10.90)
    private val berlin = GeoPlace("2", "Berlin", "Germany", "Berlin", 52.52, 13.40)

    private val anchor = IntRect(left = 600, top = 0, right = 700, bottom = 120)

    private fun show(
        places: List<GeoPlace>,
        visible: Boolean = true,
        emptyMessage: String = "No favorites yet.",
        onSelect: (GeoPlace) -> Unit = {},
        onRemove: (GeoPlace) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            FineWeatherTheme {
                FavoritesPanel(
                    visible = visible,
                    anchor = anchor,
                    places = places,
                    emptyMessage = emptyMessage,
                    onSelect = onSelect,
                    onRemove = onRemove,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun listsThePlacesAndReportsTheSelectedOne() {
        var selected: GeoPlace? = null
        show(places = listOf(augsburg, berlin), onSelect = { selected = it })

        composeTestRule.onNodeWithText("Favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Augsburg").assertIsDisplayed()
        composeTestRule.onNodeWithText("Berlin").performClick()

        assertEquals(berlin, selected)
    }

    @Test
    fun removeButton_removesThatPlace() {
        var removed: GeoPlace? = null
        show(places = listOf(augsburg, berlin), onRemove = { removed = it })

        composeTestRule.onAllNodesWithTag("removeFavoriteButton")[1].performClick()

        assertEquals(berlin, removed)
    }

    @Test
    fun withoutPlaces_showsTheEmptyMessage() {
        show(places = emptyList(), emptyMessage = "No favorites yet. Tap the star.")

        composeTestRule.onNodeWithText("No favorites yet. Tap the star.").assertIsDisplayed()
    }

    @Test
    fun withManyPlaces_capsTheHeightAndScrollsToTheLastOne() {
        val places = (1..10).map { GeoPlace("id-$it", "City $it", "Country", "Region", 1.0, 2.0) }
        show(places = places)

        composeTestRule.onNodeWithText("City 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("City 10").assertIsNotDisplayed()
        val panelHeight = composeTestRule.onNodeWithTag("favoritesPanel").getBoundsInRoot().height
        assertTrue("panel is $panelHeight tall", panelHeight <= 40.dp + 64.dp * 8 + 2.dp)

        composeTestRule.onNodeWithText("City 10").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sitsAtTheEndOfTheWindowNotUnderTheIconsOwnEdge() {
        show(places = listOf(augsburg))

        // A popup is a window of its own, so its bounds in the window start at zero: ask for the screen position.
        val panel = composeTestRule.onNodeWithTag("favoritesPanel").fetchSemanticsNode()
        val right = panel.layoutInfo.coordinates.positionOnScreen().x + panel.size.width
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val windowWidth = density.widthPixels
        // The anchor's right edge is at 700 px, far from the window's.
        assertEquals((windowWidth - 8 * density.density).toDouble(), right.toDouble(), 1.0)
    }

    @Test
    fun backPress_dismisses() {
        var dismissed = 0
        show(places = listOf(augsburg), onDismiss = { dismissed++ })

        composeTestRule.onNodeWithTag("favoritesPanel").assertIsDisplayed()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun hidesWhenNotVisibleAnymore() {
        val visible = mutableStateOf(true)
        composeTestRule.setContent {
            FineWeatherTheme {
                FavoritesPanel(
                    visible = visible.value,
                    anchor = anchor,
                    places = listOf(augsburg),
                    emptyMessage = "none",
                    onSelect = {},
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("favoritesPanel").assertIsDisplayed()

        visible.value = false

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("favoritesPanel").fetchSemanticsNodes().isEmpty()
        }
    }
}
