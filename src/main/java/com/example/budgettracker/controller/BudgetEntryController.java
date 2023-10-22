package com.example.budgettracker.controller;

import com.example.budgettracker.ChangeScene;
import com.example.budgettracker.SceneName;
import com.example.budgettracker.profiles.CurrentProfile;
import com.example.budgettracker.profiles.Expense;
import com.example.budgettracker.profiles.ProfileRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class BudgetEntryController {

    @FXML
    private ComboBox<String> savingPeriodCombo;
    @FXML
    private ComboBox<String> savingIncomeCombo;
    @FXML
    private ComboBox<String> incomeCombo;
    @FXML
    private VBox savingView;
    @FXML
    private VBox incomeView;
    @FXML
    private Button expenseButton;
    @FXML
    private Button backButton;
    @FXML
    private HBox optionView;
    @FXML
    private TextArea savingEntry;
    @FXML
    private TextArea savingIncomeEntry;
    @FXML
    private TextArea incomeEntry;
    @FXML
    private ImageView profileIcon;
    @FXML
    private ComboBox<String> currencyComboBox;

    private static final String MONTHLY = "Monthly";
    private static final String YEARLY = "Yearly";

    private Map<String, Double> conversionRates = new HashMap<>();

    private final ObservableList<String> periodOptions = FXCollections.observableArrayList("Weekly", MONTHLY, YEARLY);

    ChangeScene changeScene;

    @FXML
    public void initialize() {
        changeScene = new ChangeScene();
        List<String> currencies = CurrencyController.getAvailableCurrencies();
        Collections.sort(currencies);
        currencyComboBox.setItems(FXCollections.observableArrayList(currencies));
        /***************************************************************************************
         *    Title: How to get all currency symbols in Java
         *    Author: Damian Terlecki
         *    Date: 28/12/2020, referenced: 11/10/2023
         *    Code version: 1.0
         *    Availability: https://blog.termian.dev/posts/java-local-currency-symbols/
         * Helped with understanding how to get locale specific symbol. Adapted the code accordingly.
         *
         ***************************************************************************************/
        Map<String, String> currencyInfoMap = Arrays.stream(Locale.getAvailableLocales())
                .collect(HashMap<Locale, Currency>::new,
                        (map, locale) -> map.put(locale, getLocaleCurrency(locale)), HashMap<Locale, Currency>::putAll)
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getValue().getCurrencyCode(),
                        entry -> getCurrencySymbol(entry.getKey(), entry.getValue()),
                        (existingValue, newValue) -> existingValue
                ));
        if(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency() == null){
            currencyComboBox.setValue("EUR");
        }
        else{
            currencyComboBox.setValue(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency());
        }
        currencyComboBox.showingProperty().addListener((observable, wasShowing, isNowShowing) -> {
            Platform.runLater(() -> scrollComboboxListToIndex(currencyComboBox, currencyComboBox.getSelectionModel().getSelectedIndex()));
        });

        currencyComboBox.setOnKeyReleased(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                String s = jumpTo(event.getText(), currencyComboBox.getValue(), currencyComboBox.getItems());
                if (s != null) {
                    currencyComboBox.setValue(s);
                }
            }
        });

        currencyComboBox.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                scrollComboboxListToIndex(currencyComboBox, newValue.intValue());
            }
        });

        if(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency() == null){
            savingIncomeEntry.setPromptText(currencyInfoMap.get("EUR"));
            savingEntry.setPromptText(currencyInfoMap.get("EUR"));
            incomeEntry.setPromptText(currencyInfoMap.get("EUR"));
        }
        else{
            savingIncomeEntry.setPromptText(currencyInfoMap.get(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency()));
            savingEntry.setPromptText(currencyInfoMap.get(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency()));
            incomeEntry.setPromptText(currencyInfoMap.get(CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency()));
        }
        currencyComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (currencyInfoMap.containsKey(newValue)) {
                savingIncomeEntry.setPromptText(currencyInfoMap.get(newValue));
                savingEntry.setPromptText(currencyInfoMap.get(newValue));
                incomeEntry.setPromptText(currencyInfoMap.get(newValue));
            } else {
                savingIncomeEntry.setPromptText(Currency.getInstance(newValue).getSymbol());
                savingEntry.setPromptText(Currency.getInstance(newValue).getSymbol());
                incomeEntry.setPromptText(Currency.getInstance(newValue).getSymbol());
            }
        });
        expenseButton.setDisable(true);
        onBack(null);
        savingIncomeCombo.setItems(periodOptions);
        incomeCombo.setItems(periodOptions);
        savingPeriodCombo.setItems(periodOptions);
        savingIncomeCombo.getSelectionModel().selectFirst();
        incomeCombo.getSelectionModel().selectFirst();
        savingPeriodCombo.getSelectionModel().selectFirst();

        if (CurrentProfile.getInstance().getCurrentProfile().getProfilePicture() != null) {
            Image image = new Image("file:" + CurrentProfile.getInstance().getCurrentProfile().getProfilePicture());
            profileIcon.setImage(image);
        }

        // Enable or Disable the expense navigation button if required entries are
        // filled and is valid
        addNumericListener(savingEntry);
        addNumericListener(savingIncomeEntry);
        addNumericListener(incomeEntry);
        savingEntry.textProperty().addListener((observable, oldValue, newValue) -> updateExpenseButtonState());
        savingIncomeEntry.textProperty().addListener((observable, oldValue, newValue) -> updateExpenseButtonState());
        incomeEntry.textProperty().addListener((observable, oldValue, newValue) -> updateExpenseButtonState());
    }

    /***
     * Uses the local object, returns the corresponding currency
     * @param locale the geographical location
     * @param currency based off the location, the correct currency symbol
     * @return currency symbol
     */
    private static String getCurrencySymbol(Locale locale, Currency currency) {
        return currency.getSymbol(locale);
    }

    /***
     * Returns the corresponding Currency object
     * @param locale location of the currency code
     * @return Currency object with the currency symbol
     */
    private static Currency getLocaleCurrency(Locale locale) {
        try {
            return Currency.getInstance(locale);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    private void loadConversionRatesFromFile() {
        String filePath = "src/main/java/data/exchange_rates.json";
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject jsonObject = new JSONObject(content);

            if (jsonObject.getBoolean("success")) {
                JSONObject rates = jsonObject.getJSONObject("rates");
                for (String key : rates.keySet()) {
                    conversionRates.put(key, rates.getDouble(key));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double convertAmount(double amount, String fromCurrency, String toCurrency) {
        double fromRate = conversionRates.getOrDefault(fromCurrency, 1.0);
        double toRate = conversionRates.getOrDefault(toCurrency, 1.0);
        return (amount / fromRate) * toRate;
    }

    /**
     * This method navigates from the 'Amount to Budget' or 'Set Saving Goals' back
     * to the option selector view.
     *
     * @param event The on click event
     */
    @FXML
    public void onBack(ActionEvent event) {
        clearAllEntries();
        savingView.setVisible(false);
        incomeView.setVisible(false);
        optionView.setVisible(true);
        expenseButton.setVisible(false);
        backButton.setVisible(false);
    }

    /**
     * This method navigates to the category expenses scene.
     *
     * @param event The on click event
     */
    @FXML
    public void onExpense(ActionEvent event) throws IOException {

        // Get the current currency from the user's profile and the selected currency from the ComboBox
        String currentCurrency = CurrentProfile.getInstance().getCurrentProfile().getCurrentCurrency();
        String selectedCurrency = currencyComboBox.getValue();
        CurrentProfile.getInstance().getCurrentProfile().setCurrencySymbol(incomeEntry.getPromptText());
        // Save the new user data
        saveUserEntryData(currentCurrency, selectedCurrency);

        // navigate to expense categorise view
        changeScene.changeScene(event, SceneName.BUDGET_CATEGORIES);
    }

    /**
     * This method navigates to the Profile Select Scene
     *
     * @param event The mouse on click event
     * @throws IOException
     */
    @FXML
    public void onProfileSelect(MouseEvent event) throws IOException {
        changeScene.changeScene(event, SceneName.SELECT_PROFILE);
    }

    /**
     * This helped method saves the user data depending on current tab to the JSON
     * always in weekly format
     */
    private void saveUserEntryData(String fromCurrency, String toCurrency) throws IOException {

        loadConversionRatesFromFile();

        for (Expense expense : CurrentProfile.getInstance().getCurrentProfile().getExpenses()) {
            expense.setCost(convertAmount(expense.getCost(), fromCurrency, toCurrency));
        }

        // checks which page was open
        if (!savingIncomeEntry.getText().isEmpty()) {
            int income = Integer.parseInt(savingIncomeEntry.getText());
            int saving = Integer.parseInt(savingEntry.getText());

            String incomePeriod = savingIncomeCombo.getValue();
            String savingPeriod = savingPeriodCombo.getValue();

            // convert to always want data weekly
            if (incomePeriod.equals(MONTHLY)) {
                income = (income * 12) / 52;
            } else if (incomePeriod.equals(YEARLY)) {
                income = income / 52;
            }

            if (savingPeriod.equals(MONTHLY)) {
                saving = (saving * 12) / 52;
            } else if (savingPeriod.equals(YEARLY)) {
                saving = saving / 52;
            }

            // budget = income - saving goal
            CurrentProfile.getInstance().getCurrentProfile().setBudget(income - saving);
            CurrentProfile.getInstance().getCurrentProfile().setIncome(income);
            CurrentProfile.getInstance().getCurrentProfile().setSavings(saving);
        } else {
            int income = Integer.parseInt(incomeEntry.getText());

            String budgetPeriod = incomeCombo.getValue();

            // convert to always weekly data
            if (budgetPeriod.equals(MONTHLY)) {
                income = (income * 12) / 52;
            } else if (budgetPeriod.equals(YEARLY)) {
                income = income / 52;
            }

            CurrentProfile.getInstance().getCurrentProfile().setBudget(income);
            CurrentProfile.getInstance().getCurrentProfile().setIncome(income);
            CurrentProfile.getInstance().getCurrentProfile().setSavings(0);

        }

        // Set the selected currency to the current profile's currentCurrency
        CurrentProfile.getInstance().getCurrentProfile().setCurrentCurrency(toCurrency);

        ProfileRepository profileRepository = new ProfileRepository();
        profileRepository.saveProfile(CurrentProfile.getInstance().getCurrentProfile());
    }

    /**
     * This method shows the 'Set Saving Goals' view.
     *
     * @param event The on click event
     */
    @FXML
    public void onSaving(ActionEvent event) {
        savingView.setVisible(true);
        incomeView.setVisible(false);
        optionView.setVisible(false);
        expenseButton.setVisible(true);
        backButton.setVisible(true);
    }

    /**
     * This method shows the 'Amount to Budget' view.
     *
     * @param event The on click event
     */
    @FXML
    public void onIncome(ActionEvent event) {
        incomeView.setVisible(true);
        savingView.setVisible(false);
        optionView.setVisible(false);
        expenseButton.setVisible(true);
        backButton.setVisible(true);
    }

    /**
     * This method clears all TextArea views.
     */
    private void clearAllEntries() {
        savingEntry.clear();
        savingIncomeEntry.clear();
        incomeEntry.clear();
    }

    /**
     * This method disables/enables the categorise expense navigation button.
     */
    private void updateExpenseButtonState() {
        if (savingView.isVisible()) {
            boolean hasValidSavingEntry = isNumeric(savingEntry.getText()) && isNumeric(savingIncomeEntry.getText());
            expenseButton.setDisable(!hasValidSavingEntry);
        } else if (incomeView.isVisible()) {
            boolean hasValidIncomeEntry = isNumeric(incomeEntry.getText());
            expenseButton.setDisable(!hasValidIncomeEntry);
        }
    }

    /**
     * This method takes a String and checks if it is numeric
     *
     * @param text The String to check numeric for
     * @return boolean
     */
    private boolean isNumeric(String text) {
        return text.matches("\\d+(\\.\\d+)?");
    }

    /**
     * This method takes a TextArea and adds non-numeric characters preventer
     * listener
     *
     * @param textField The TextArea to add the numeric listener to
     */
    private void addNumericListener(TextArea textField) {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                textField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    /**
     * This method takes the user to the edit profile scene
     *
     * @param event The mouse on click event
     * @throws IOException If the scene cannot be changed
     */
    @FXML
    public void onProfileIconClick(MouseEvent event) throws IOException {
        changeScene.changeScene(event, SceneName.EDIT_PROFILE);
    }

    private static String jumpTo(String keyPressed, String currentlySelected, List<String> items) {
        String key = keyPressed.toUpperCase();
        if (key.matches("^[A-Z]$")) {
            // Only act on letters so that navigating with cursor keys does not
            // try to jump somewhere.
            boolean letterFound = false;
            boolean foundCurrent = currentlySelected == null;
            for (String s : items) {
                if (s.toUpperCase().startsWith(key)) {
                    letterFound = true;
                    if (foundCurrent) {
                        return s;
                    }
                    foundCurrent = s.equals(currentlySelected);
                }
            }
            if (letterFound) {
                return jumpTo(keyPressed, null, items);
            }
        }
        return null;
    }

    private void scrollComboboxListToIndex(ComboBox<?> comboBox, int index) {
        ComboBoxListViewSkin<?> skin = (ComboBoxListViewSkin<?>) comboBox.getSkin();
        ListView<?> list = (ListView<?>) skin.getPopupContent();
        list.scrollTo(index);
    }
}

