package dev.personalagenda;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.data.Database;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.data.CategoryRepository;
import dev.personalagenda.data.WorkSessionRepository;
import dev.personalagenda.view.TimelineView;
import dev.personalagenda.view.TaskStepsView;
import dev.personalagenda.view.ProjectDashboard;
import dev.personalagenda.view.FloatingWidget;
import dev.personalagenda.view.AppIcons;
import dev.personalagenda.view.UiLayout;
import dev.personalagenda.view.InsightsView;
import dev.personalagenda.service.FocusSessionService;

// import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Separator;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.event.Event;

public class App extends Application {

    private final CategoryRepository categoryRepository = new CategoryRepository();

    private final Map<String, String> categoryColors = new LinkedHashMap<>();

    private final TaskStepRepository taskStepRepository = new TaskStepRepository();

    private final Label dailyProgressDetails = new Label();

    private BiConsumer<Task, TaskStep> scheduleRequest;
    private BiConsumer<Task, TaskStep> focusRequest;

    @Override
    public void start(Stage stage) throws Exception {

        Database.initialize();
        categoryColors.putAll(categoryRepository.findAll());
        TaskRepository repository = new TaskRepository();

        System.out.println(
                "Saved tasks to database: " + repository.findAll().size());

        List<Task> tasks = repository.findAll();

        Label title = new Label("Personal Agenda");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter a task: ");
        ComboBox<String> taskCategoryInput = new ComboBox<>();

        taskCategoryInput.getItems().setAll(
                categoryColors.keySet());

        taskCategoryInput.setValue("General");
        taskCategoryInput.setPrefWidth(110);

        Button addButton = new Button("Add");

        VBox taskList = new VBox(8);

        ComboBox<String> viewFilter = new ComboBox<>();
        viewFilter.getItems().addAll("Today", "Tomorrow", "Replan", "Backlog", "Earlier", "All");
        viewFilter.setValue("Today");

        Spinner<Integer> durationInput = new Spinner<>(15, 480, 60, 15);
        durationInput.setPrefWidth(100);

        DatePicker plannedDateInput = new DatePicker(LocalDate.now());
        plannedDateInput.setPrefWidth(150);

        Button backlogButton = new Button("Backlog");
        backlogButton.setOnAction(event -> {
            plannedDateInput.setValue(null);
        });

        Label progressLabel = new Label("0 / 0 completed");

        ProgressBar progressBar = new ProgressBar(0);

        refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());

        viewFilter.setOnAction(event -> {
            refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());
        });

        addButton.setOnAction(event -> {

            String taskTitle = taskInput.getText().trim();

            if (taskTitle.isEmpty()) {
                return;
            }

            int minutes = durationInput.getValue();
            Task newTask = new Task(taskTitle, minutes);
            newTask.setPlannedDate(plannedDateInput.getValue());

            try {
                repository.insert(newTask);
                categoryRepository.assign(
                        newTask.getId(),
                        taskCategoryInput.getValue());
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            tasks.add(newTask);

            if (matchesFilter(newTask, viewFilter.getValue())) {
                VBox taskEntry = createTaskRow(
                        newTask, tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());

                taskList.getChildren().add(taskEntry);
            } else {
                viewFilter.setValue("All");
            }

            taskInput.clear();

            updateProgress(tasks, progressLabel, progressBar, viewFilter.getValue());
        });

        taskInput.setOnAction(event -> addButton.fire());

        TimelineView timeline = new TimelineView();

        progressLabel.setWrapText(true);

        progressLabel.setMaxWidth(Double.MAX_VALUE);
        progressLabel.setMinHeight(Region.USE_PREF_SIZE);
        progressLabel.setTextOverrun(OverrunStyle.CLIP);

        progressBar.setMinHeight(12);
        progressBar.setPrefHeight(12);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.getStyleClass().add("daily-progress");

        VBox dailyProgressBox = new VBox(
                8,
                progressLabel,
                progressBar,
                dailyProgressDetails);

        dailyProgressBox.setFillWidth(true);
        dailyProgressBox.setMaxWidth(Double.MAX_VALUE);

        progressBar.setMinWidth(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        dailyProgressDetails.setWrapText(true);
        dailyProgressDetails.setMaxWidth(Double.MAX_VALUE);

        dailyProgressBox.setFillWidth(true);

        timeline.getChildren().add(1, dailyProgressBox);

        TimeBlockRepository blockRepository = new TimeBlockRepository();

        VBox scheduledBlocks = new VBox(8);

        DatePicker scheduleDateInput = new DatePicker(LocalDate.now());

        refreshSchedule(blockRepository, timeline, scheduledBlocks, scheduleDateInput.getValue());

        scheduleDateInput.valueProperty().addListener((observable, oldDate, newDate) -> {

            if (newDate == null) {
                scheduleDateInput.setValue(oldDate);
                return;
            }

            try {
                refreshSchedule(blockRepository, timeline, scheduledBlocks, newDate);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        Label scheduleTitle = new Label("Day Plan");

        HBox scheduleHeader = new HBox(12, scheduleTitle, scheduleDateInput);

        Region scheduleHeaderSpacer = new Region();

        scheduleHeader.getChildren().add(
                1, scheduleHeaderSpacer);

        HBox.setHgrow(
                scheduleHeaderSpacer, Priority.ALWAYS);

        scheduleHeader.setAlignment(Pos.CENTER_LEFT);

        Button startCurrentButton = new Button("Start Current");

        startCurrentButton.setMinWidth(Region.USE_PREF_SIZE);

        // Insert between the spacer and date picker.
        scheduleHeader.getChildren().add(2, startCurrentButton);

        startCurrentButton.setOnAction(event -> {
            try {
                LocalDateTime now = LocalDateTime.now();

                List<TimeBlock> currentBlocks = blockRepository
                        .findForDate(now.toLocalDate())
                        .stream()
                        .filter(block -> !now.isBefore(block.start())
                                && now.isBefore(block.end()))
                        .toList();

                if (currentBlocks.size() != 1) {
                    showFocusMessage(
                            stage,
                            currentBlocks.isEmpty()
                                    ? "No activity is currently scheduled."
                                    : "Multiple activities overlap. "
                                            + "Please choose a Task in To-Do.");
                    return;
                }

                TimeBlock current = currentBlocks.get(0);

                if (current.type() != TimeBlock.Type.TASK
                        || current.taskId() == null) {

                    showFocusMessage(
                            stage,
                            "Current activity is not linked to a Task.");
                    return;
                }

                Task task = repository.findAll().stream()
                        .filter(t -> t.getId().equals(current.taskId()))
                        .findFirst()
                        .orElse(null);

                if (task == null) {
                    showFocusMessage(stage, "Linked Task no longer exists.");
                    return;
                }

                TaskStep step = null;

                if (current.stepId() != null) {
                    step = taskStepRepository
                            .findByTaskId(task.getId())
                            .stream()
                            .filter(s -> s.id().equals(current.stepId()))
                            .findFirst()
                            .orElse(null);

                    if (step == null) {
                        showFocusMessage(stage, "Linked Step no longer exists.");
                        return;
                    }
                }

                if (focusRequest != null) {
                    focusRequest.accept(task, step);
                }

            } catch (SQLException ex) {
                showFocusMessage(
                        stage,
                        "Unable to load current activity: " + ex.getMessage());
            }
        });

        scheduleDateInput.setMinWidth(Region.USE_PREF_SIZE);

        TextField blockTitleInput = new TextField();
        blockTitleInput.setPromptText("Activity name");

        LocalTime initialStart = LocalTime.now()
                .truncatedTo(ChronoUnit.MINUTES);

        TextField startTimeInput = new TextField(initialStart.toString());

        TextField endTimeInput = new TextField(initialStart.plusHours(1).toString());

        startTimeInput.setPrefColumnCount(5);
        endTimeInput.setPrefColumnCount(5);

        ComboBox<TimeBlock.Type> blockTypeInput = new ComboBox<>();
        blockTypeInput.getItems().addAll(TimeBlock.Type.values());
        blockTypeInput.setValue(TimeBlock.Type.TASK);

        ComboBox<Task> linkedTaskInput = new ComboBox<>();

        ComboBox<TaskStep> linkedStepInput = createStepCombo();

        linkedStepInput.setOnShowing(event -> reloadTaskSteps(
                linkedStepInput,
                linkedTaskInput.getValue()));

        linkedTaskInput.setPromptText("Choose an existing task");
        linkedTaskInput.setPrefWidth(200);

        linkedTaskInput.setOnShowing(event -> {
            try {
                String selectedId = linkedTaskInput.getValue() == null
                        ? null
                        : linkedTaskInput.getValue().getId();

                List<Task> available = repository.findAll();

                linkedTaskInput.getItems().setAll(available);

                Task selected = available.stream()
                        .filter(task -> task.getId().equals(selectedId))
                        .findFirst()
                        .orElse(null);

                linkedTaskInput.setValue(selected);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        Button wholeTaskButton = new Button("Whole task");

        wholeTaskButton.setOnAction(event -> linkedStepInput.setValue(null));

        Label linkedStepLabel = new Label("Linked step");
        linkedStepLabel.setPrefWidth(80);
        linkedStepLabel.setMinWidth(80);

        HBox linkedStepRow = new HBox(
                12,
                linkedStepLabel,
                linkedStepInput,
                wholeTaskButton);

        linkedStepRow.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(linkedStepInput, Priority.ALWAYS);
        linkedStepInput.setMaxWidth(Double.MAX_VALUE);

        wholeTaskButton.setMinWidth(Region.USE_PREF_SIZE);

        linkedStepRow.setVisible(false);
        linkedStepRow.setManaged(false);

        linkedTaskInput.valueProperty().addListener(
                (observable, oldTask, newTask) -> {

                    reloadTaskSteps(linkedStepInput, newTask);

                    if (newTask != null) {
                        blockTitleInput.setText(newTask.getTitle());
                    }

                    boolean showStep = newTask != null
                            && blockTypeInput.getValue() == TimeBlock.Type.TASK;

                    linkedStepRow.setVisible(showStep);
                    linkedStepRow.setManaged(showStep);
                });

        Label linkedTaskLabel = new Label("Linked task");
        linkedTaskLabel.setPrefWidth(80);
        linkedTaskLabel.setMinWidth(80);

        HBox linkedTaskRow = new HBox(
                12, linkedTaskLabel, linkedTaskInput);

        linkedTaskRow.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(linkedTaskInput, Priority.ALWAYS);
        linkedTaskInput.setMaxWidth(Double.MAX_VALUE);

        linkedTaskRow.setVisible(true);
        linkedTaskRow.setManaged(true);

        blockTypeInput.valueProperty().addListener(
                (observable, oldType, newType) -> {

                    boolean isTask = newType == TimeBlock.Type.TASK;

                    linkedTaskRow.setVisible(isTask);
                    linkedTaskRow.setManaged(isTask);

                    boolean showStep = isTask && linkedTaskInput.getValue() != null;

                    linkedStepRow.setVisible(showStep);
                    linkedStepRow.setManaged(showStep);

                    if (!isTask) {
                        linkedTaskInput.setValue(null);
                    }
                });

        CheckBox nextDayInput = new CheckBox("Ends next day (+1 day)");

        HBox nextDayRow = new HBox(8, nextDayInput);
        nextDayRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox autoEndInput = new CheckBox("Auto end from estimate");

        autoEndInput.getStyleClass().add("schedule-option");
        nextDayInput.getStyleClass().add("schedule-option");

        autoEndInput.setSelected(true);

        nextDayRow.getChildren().add(0, autoEndInput);

        Runnable recalculateEnd = () -> {

            if (!autoEndInput.isSelected()
                    || blockTypeInput.getValue() != TimeBlock.Type.TASK
                    || linkedTaskInput.getValue() == null
                    || scheduleDateInput.getValue() == null) {
                return;
            }

            int minutes = linkedStepInput.getValue() != null
                    ? linkedStepInput.getValue().estimatedMinutes()
                    : linkedTaskInput.getValue().getEstimatedMinutes();

            if (minutes <= 0) {
                return;
            }

            try {
                LocalDateTime start = scheduleDateInput.getValue().atTime(
                        LocalTime.parse(startTimeInput.getText().trim()));

                LocalDateTime end = start.plusMinutes(minutes);

                endTimeInput.setText(end.toLocalTime().toString());

                nextDayInput.setSelected(
                        end.toLocalDate().isAfter(start.toLocalDate()));

            } catch (DateTimeParseException ex) {
                // Wait until the user finishes entering a valid time.
            }
        };

        Runnable updateAutoMode = () -> {

            boolean active = autoEndInput.isSelected()
                    && blockTypeInput.getValue() == TimeBlock.Type.TASK
                    && linkedTaskInput.getValue() != null;

            endTimeInput.setEditable(!active);
            nextDayInput.setDisable(active);

            recalculateEnd.run();
        };

        linkedTaskInput.valueProperty().addListener(
                (obs, oldValue, newValue) -> updateAutoMode.run());

        linkedStepInput.valueProperty().addListener(
                (obs, oldValue, newValue) -> recalculateEnd.run());

        startTimeInput.textProperty().addListener(
                (obs, oldValue, newValue) -> recalculateEnd.run());

        scheduleDateInput.valueProperty().addListener(
                (obs, oldValue, newValue) -> recalculateEnd.run());

        blockTypeInput.valueProperty().addListener(
                (obs, oldValue, newValue) -> updateAutoMode.run());

        autoEndInput.selectedProperty().addListener(
                (obs, oldValue, newValue) -> updateAutoMode.run());

        updateAutoMode.run();

        Button saveBlockButton = new Button("Day Plan");
        saveBlockButton.setText("Add Activity");

        Label blockStatus = new Label();

        saveBlockButton.setOnAction(event -> {

            String titleText = blockTitleInput.getText().trim();

            if (titleText.isEmpty()) {
                blockStatus.setText("Enter an activity name.");
                return;
            }

            try {

                LocalDate today = scheduleDateInput.getValue();

                if (today == null) {
                    blockStatus.setText("Please select a date.");
                    return;
                }

                LocalDateTime start = today.atTime(
                        LocalTime.parse(startTimeInput.getText().trim()));

                LocalDateTime end = today.atTime(
                        LocalTime.parse(endTimeInput.getText().trim()));

                if (nextDayInput.isSelected()) {
                    end = end.plusDays(1);
                }

                if (!end.isAfter(start)) {
                    blockStatus.setText(
                            "End must be after start.");
                    return;
                }

                String linkedTaskId = blockTypeInput.getValue() == TimeBlock.Type.TASK
                        && linkedTaskInput.getValue() != null
                                ? linkedTaskInput.getValue().getId()
                                : null;

                if (linkedTaskId != null && !repository.exists(linkedTaskId)) {
                    linkedTaskInput.setValue(null);
                    blockStatus.setText(
                            "Linked task no longer exists. Please select another.");
                    return;
                }

                String linkedStepId = linkedTaskId != null
                        && linkedStepInput.getValue() != null
                                ? linkedStepInput.getValue().id()
                                : null;

                if (linkedStepId != null
                        && taskStepRepository.findByTaskId(linkedTaskId)
                                .stream()
                                .noneMatch(step -> step.id().equals(linkedStepId))) {

                    blockStatus.setText("Linked step no longer exists.");
                    reloadTaskSteps(linkedStepInput, linkedTaskInput.getValue());
                    return;
                }

                TimeBlock block = new TimeBlock(
                        titleText,
                        start,
                        end,
                        blockTypeInput.getValue(),
                        linkedTaskId,
                        linkedStepId);

                blockRepository.insert(block);

                refreshSchedule(blockRepository, timeline, scheduledBlocks, today);

                blockTitleInput.clear();
                linkedTaskInput.setValue(null);
                blockStatus.setText("Scheduled: " + titleText);

            } catch (DateTimeParseException ex) {
                blockStatus.setText("Use time format HH:mm.");
            } catch (SQLException ex) {
                blockStatus.setText("Database save failed.");
                ex.printStackTrace();
            }
        });

        HBox inputRow = new HBox(
                10, taskInput, durationInput, addButton);

        inputRow.getChildren().add(taskCategoryInput);

        HBox.setHgrow(taskInput, Priority.ALWAYS);

        taskInput.setMinWidth(120);
        taskInput.setMaxWidth(Double.MAX_VALUE);

        inputRow.setAlignment(Pos.CENTER_LEFT);

        HBox dateRow = new HBox(
                10, new Label("Planned date:"), plannedDateInput, backlogButton);

        inputRow.setAlignment(Pos.CENTER_LEFT);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        addButton.setMinWidth(Region.USE_PREF_SIZE);
        backlogButton.setMinWidth(Region.USE_PREF_SIZE);

        durationInput.setMinWidth(Region.USE_PREF_SIZE);
        taskCategoryInput.setMinWidth(Region.USE_PREF_SIZE);
        plannedDateInput.setMinWidth(Region.USE_PREF_SIZE);

        nextDayInput.setText("Ends next day (+1 day)");

        // HBox blockInputRow = new HBox(
        // 8, blockTitleInput, blockTypeInput, saveBlockButton);

        startTimeInput.setPrefWidth(105);
        endTimeInput.setPrefWidth(105);

        startTimeInput.setMaxWidth(105);
        endTimeInput.setMaxWidth(105);

        blockTitleInput.setPrefWidth(230);
        blockTitleInput.setMinWidth(140);

        // HBox.setHgrow(blockTitleInput, Priority.ALWAYS);

        blockTypeInput.setPrefWidth(115);

        linkedTaskInput.setPrefWidth(260);

        GridPane scheduleFormGrid = new GridPane();
        scheduleFormGrid.setHgap(12);
        scheduleFormGrid.setVgap(12);
        scheduleFormGrid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(80);

        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);

        ColumnConstraints labelCol2 = new ColumnConstraints();
        labelCol2.setPrefWidth(44);

        ColumnConstraints controlCol2 = new ColumnConstraints();
        controlCol2.setHgrow(Priority.ALWAYS);
        controlCol2.setFillWidth(true);

        scheduleFormGrid.getColumnConstraints().addAll(
                labelCol, controlCol, labelCol2, controlCol2);

        scheduleFormGrid.add(new Label("Activity"), 0, 0);
        scheduleFormGrid.add(blockTitleInput, 1, 0);

        scheduleFormGrid.add(new Label("Type"), 2, 0);
        scheduleFormGrid.add(blockTypeInput, 3, 0);

        scheduleFormGrid.add(new Label("Start"), 0, 1);
        scheduleFormGrid.add(startTimeInput, 1, 1);

        scheduleFormGrid.add(new Label("End"), 2, 1);
        scheduleFormGrid.add(endTimeInput, 3, 1);

        // Row 2: automatic time options
        scheduleFormGrid.add(nextDayRow, 0, 2, 4, 1);

        // Row 3: linked task
        scheduleFormGrid.add(linkedTaskRow, 0, 3, 4, 1);

        // Row 4: optional linked step
        scheduleFormGrid.add(linkedStepRow, 0, 4, 4, 1);

        // Row 5: save action, aligned to the right
        saveBlockButton.setMinWidth(Region.USE_PREF_SIZE);

        scheduleFormGrid.add(saveBlockButton, 3, 5);

        GridPane.setHalignment(
                saveBlockButton,
                javafx.geometry.HPos.RIGHT);

        VBox activityForm = new VBox(
                10,
                scheduleFormGrid,
                blockStatus);

        TitledPane addActivityPane = new TitledPane(
                "+ Add activity",
                activityForm);

        activityForm.setMaxHeight(Region.USE_PREF_SIZE);

        addActivityPane.setMaxHeight(Region.USE_PREF_SIZE);

        VBox.setVgrow(
                addActivityPane,
                Priority.NEVER);

        addActivityPane.setExpanded(false);
        addActivityPane.setAnimated(false);

        Label activitiesTitle = new Label("Planned Activities");

        activitiesTitle.getStyleClass().add("section-title");

        VBox scheduleEditor = new VBox(
                14,
                scheduleHeader,
                addActivityPane,
                activitiesTitle,
                scheduledBlocks);

        ComboBox<String> colorCategoryInput = new ComboBox<>();
        colorCategoryInput.getItems().setAll(categoryColors.keySet());
        colorCategoryInput.setValue("General");

        ColorPicker categoryColorPicker = new ColorPicker(
                Color.web(categoryColors.get("General")));

        colorCategoryInput.setOnAction(event -> {

            String name = colorCategoryInput.getValue();

            if (name != null && categoryColors.containsKey(name)) {
                categoryColorPicker.setValue(
                        Color.web(categoryColors.get(name)));
            }
        });

        TextField newCategoryInput = new TextField();
        newCategoryInput.setPromptText("New category name (optional)");

        Button saveCategoryButton = new Button("Save Color");
        Label categoryStatus = new Label();

        saveCategoryButton.setOnAction(event -> {

            String name = newCategoryInput.getText().trim();

            if (name.isEmpty()) {
                name = colorCategoryInput.getValue();
            }

            if (name == null || name.isBlank()) {
                categoryStatus.setText("Choose a category.");
                return;
            }

            Color color = categoryColorPicker.getValue();

            String hex = String.format(
                    "#%02X%02X%02X",
                    (int) Math.round(color.getRed() * 255),
                    (int) Math.round(color.getGreen() * 255),
                    (int) Math.round(color.getBlue() * 255));

            try {
                categoryRepository.save(name, hex);

                categoryColors.clear();
                categoryColors.putAll(categoryRepository.findAll());

                String selectedTaskCategory = taskCategoryInput.getValue();

                taskCategoryInput.getItems().setAll(
                        categoryColors.keySet());

                taskCategoryInput.setValue(
                        selectedTaskCategory == null
                                ? name
                                : selectedTaskCategory);

                colorCategoryInput.getItems().setAll(
                        categoryColors.keySet());
                colorCategoryInput.setValue(name);

                newCategoryInput.clear();
                categoryStatus.setText("Saved: " + name);

                refreshTaskList(
                        tasks, taskList, progressLabel, progressBar,
                        repository, viewFilter.getValue());

                refreshSchedule(
                        blockRepository,
                        timeline,
                        scheduledBlocks,
                        scheduleDateInput.getValue());

            } catch (SQLException ex) {
                categoryStatus.setText("Category save failed.");
                ex.printStackTrace();
            }
        });

        FlowPane categoryControls = new FlowPane(
                10, 8,
                colorCategoryInput,
                categoryColorPicker,
                saveCategoryButton);

        categoryControls.setAlignment(Pos.CENTER_LEFT);
        colorCategoryInput.setMinWidth(Region.USE_PREF_SIZE);
        categoryColorPicker.setMinWidth(Region.USE_PREF_SIZE);
        saveCategoryButton.setMinWidth(Region.USE_PREF_SIZE);

        newCategoryInput.setMaxWidth(Double.MAX_VALUE);

        saveCategoryButton.setMinWidth(Region.USE_PREF_SIZE);

        VBox categoryEditor = new VBox(
                8,
                categoryControls,
                newCategoryInput,
                categoryStatus);

        TitledPane categoryManager = new TitledPane(
                "Manage Categories", categoryEditor);

        categoryManager.setExpanded(false);
        categoryManager.setAnimated(false);
        categoryManager.setMaxWidth(Double.MAX_VALUE);

        Label quickAddTitle = new Label("Quick Add");
        Label taskListTitle = new Label("Task List");

        quickAddTitle.getStyleClass().add("section-heading");
        taskListTitle.getStyleClass().add("section-heading");

        VBox taskComposer = new VBox(
                10, quickAddTitle, inputRow, dateRow);

        taskComposer.getStyleClass().add("task-composer");

        Region toolbarSpacer = new Region();

        HBox taskToolbar = new HBox(
                12, taskListTitle, toolbarSpacer, viewFilter);

        HBox.setHgrow(
                toolbarSpacer, Priority.ALWAYS);

        taskToolbar.setAlignment(
                javafx.geometry.Pos.CENTER_LEFT);

        VBox taskPanel = new VBox(
                16,
                taskComposer,
                categoryManager,
                taskToolbar,
                taskList);

        VBox schedulePanel = new VBox(
                16,
                scheduleEditor);

        ProjectDashboard projectDashboard = new ProjectDashboard(categoryColors);

        VBox categoryPanel = new VBox(
                20,
                projectDashboard);

        taskPanel.getStyleClass().add("content-card");
        schedulePanel.getStyleClass().add("content-card");
        categoryPanel.getStyleClass().add("content-card");

        ScrollPane taskScroll = new ScrollPane(taskPanel);
        taskScroll.setFitToWidth(true);

        ScrollPane scheduleScroll = new ScrollPane(schedulePanel);
        scheduleScroll.setFitToWidth(true);

        ScrollPane categoryScroll = new ScrollPane(categoryPanel);
        categoryScroll.setFitToWidth(true);

        Tab taskTab = new Tab("To-Do", taskScroll);
        taskTab.setClosable(false);

        Tab scheduleTab = new Tab("Day Plan", scheduleScroll);
        scheduleTab.setClosable(false);

        scheduleTab.setOnSelectionChanged(event -> {

            if (!scheduleTab.isSelected()) {
                return;
            }

            try {
                refreshSchedule(
                        blockRepository,
                        timeline,
                        scheduledBlocks,
                        scheduleDateInput.getValue());

            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        Tab categoryTab = new Tab("Projects", categoryScroll);
        categoryTab.setOnSelectionChanged(event -> {
            if (categoryTab.isSelected()) {
                projectDashboard.refresh();
            }
        });
        categoryTab.setClosable(false);

        InsightsView insightsView = new InsightsView(new WorkSessionRepository());

        ScrollPane insightsScroll = new ScrollPane(insightsView);
        insightsScroll.setFitToWidth(true);

        Tab insightsTab = new Tab("Insights", insightsScroll);
        insightsTab.setClosable(false);

        insightsTab.setOnSelectionChanged(event -> {
            if (insightsTab.isSelected()) {
                insightsView.reload();
            }
        });

        TabPane leftTabs = new TabPane(
                taskTab, scheduleTab, categoryTab, insightsTab);

        scheduleRequest = (requestedTask, requestedStep) -> {

            leftTabs.getSelectionModel().select(scheduleTab);
            addActivityPane.setExpanded(true);
            scheduleScroll.setVvalue(0);

            try {
                List<Task> available = repository.findAll();

                Task selectedTask = available.stream()
                        .filter(t -> t.getId().equals(requestedTask.getId()))
                        .findFirst()
                        .orElse(null);

                if (selectedTask == null) {
                    blockStatus.setText("Task no longer exists.");
                    return;
                }

                LocalDate targetDate = selectedTask.getPlannedDate();

                if (targetDate == null
                        || targetDate.isBefore(LocalDate.now())) {
                    targetDate = LocalDate.now();
                }

                scheduleDateInput.setValue(targetDate);

                if (targetDate.equals(LocalDate.now())) {
                    startTimeInput.setText(
                            LocalTime.now()
                                    .truncatedTo(ChronoUnit.MINUTES)
                                    .toString());
                }

                blockTypeInput.setValue(TimeBlock.Type.TASK);

                linkedTaskInput.getItems().setAll(available);
                linkedTaskInput.setValue(selectedTask);

                reloadTaskSteps(linkedStepInput, selectedTask);

                if (requestedStep != null) {

                    TaskStep selectedStep = linkedStepInput.getItems()
                            .stream()
                            .filter(s -> s.id().equals(requestedStep.id()))
                            .findFirst()
                            .orElse(null);

                    if (selectedStep == null) {
                        blockStatus.setText(
                                "Step no longer exists. Please choose another.");
                        return;
                    }

                    linkedStepInput.setValue(selectedStep);

                    blockTitleInput.setText(
                            selectedTask.getTitle() + " / " + selectedStep.title());

                } else {
                    linkedStepInput.setValue(null);
                    blockTitleInput.setText(selectedTask.getTitle());
                }

                autoEndInput.setSelected(true);
                recalculateEnd.run();

                blockStatus.setText(
                        "Ready to schedule. Choose the start time.");

            } catch (SQLException ex) {
                blockStatus.setText("Unable to load task.");
                ex.printStackTrace();
            }
        };

        leftTabs.setMinWidth(420);

        SplitPane layout = new SplitPane(
                leftTabs,
                timeline);

        layout.setDividerPositions(0.62);
        layout.setPadding(new Insets(20));
        layout.getStyleClass().add("app-root");
        // mainColumn.getStyleClass().add("content-card");
        timeline.getStyleClass().add("timeline-card");

        Scene scene = new Scene(layout, 1080, 640);
        stage.setMinWidth(960);
        stage.setMinHeight(600);

        scene.getStylesheets().add(
                getClass().getResource("/styles/light.css").toExternalForm());

        stage.setTitle("Personal Agenda");
        AppIcons.apply(stage);
        stage.setScene(scene);

        FocusSessionService focusService = new FocusSessionService(new WorkSessionRepository());

        FloatingWidget floatingWidget = new FloatingWidget(
                stage,
                () -> {
                    try {
                        tasks.clear();
                        tasks.addAll(repository.findAll());

                        refreshTaskList(
                                tasks,
                                taskList,
                                progressLabel,
                                progressBar,
                                repository,
                                viewFilter.getValue());

                        projectDashboard.refresh();

                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                },
                focusService);

        focusRequest = (requestedTask, requestedStep) -> {
            try {
                // Do not replace an existing running or paused session.
                if (focusService.state() != FocusSessionService.State.IDLE) {
                    floatingWidget.show();
                    return;
                }

                // An unfinished database session must be recovered first.
                WorkSessionRepository sessionRepository = new WorkSessionRepository();

                if (sessionRepository.findActive().isPresent()) {
                    floatingWidget.show();
                    return;
                }

                // Fetch the latest task rather than trusting an old UI object.
                Task selectedTask = repository.findAll().stream()
                        .filter(t -> t.getId().equals(requestedTask.getId()))
                        .findFirst()
                        .orElse(null);

                if (selectedTask == null) {
                    showFocusMessage(stage, "Task no longer exists.");
                    return;
                }

                TaskStep selectedStep = null;

                if (requestedStep != null) {
                    selectedStep = taskStepRepository
                            .findByTaskId(selectedTask.getId())
                            .stream()
                            .filter(s -> s.id().equals(requestedStep.id()))
                            .findFirst()
                            .orElse(null);

                    if (selectedStep == null) {
                        showFocusMessage(stage, "Step no longer exists.");
                        return;
                    }
                }

                focusService.start(selectedTask, selectedStep);
                floatingWidget.show();

            } catch (SQLException | IllegalArgumentException
                    | IllegalStateException ex) {

                showFocusMessage(stage,
                        "Unable to start Focus: " + ex.getMessage());
            }
        };

        Button openWidgetButton = new Button("Open Mini Widget");

        openWidgetButton.setOnAction(
                event -> floatingWidget.show());

        MenuButton windowMenu = new MenuButton("Window");

        MenuItem miniOnlyItem = new MenuItem("Hide Main · Keep Mini");

        MenuItem exitItem = new MenuItem("Exit Agenda");

        windowMenu.getItems().addAll(
                miniOnlyItem,
                exitItem);

        // Enter Mini-only mode.
        miniOnlyItem.setOnAction(event -> floatingWidget.showMiniOnly());

        // Request an application exit using the existing
        // main-window close confirmation.
        exitItem.setOnAction(event -> {

            WindowEvent closeRequest = new WindowEvent(
                    stage,
                    WindowEvent.WINDOW_CLOSE_REQUEST);

            Event.fireEvent(stage, closeRequest);

            if (!closeRequest.isConsumed()) {
                stage.hide();
            }
        });

        HBox windowControls = new HBox(
                8,
                openWidgetButton,
                windowMenu);

        windowControls.setAlignment(Pos.CENTER_LEFT);

        timeline.getChildren().add(0, windowControls);

        stage.show();
    }

    private ComboBox<TaskStep> createStepCombo() {

        ComboBox<TaskStep> combo = new ComboBox<>();
        combo.setPrefWidth(250);
        combo.setPromptText("Whole task");

        combo.setConverter(new javafx.util.StringConverter<>() {

            @Override
            public String toString(TaskStep step) {
                return step == null
                        ? "Whole task"
                        : step.title() + " · " + step.estimatedMinutes() + " min";
            }

            @Override
            public TaskStep fromString(String text) {
                return null;
            }
        });

        return combo;
    }

    private void reloadTaskSteps(
            ComboBox<TaskStep> combo, Task task) {

        String previousId = combo.getValue() == null
                ? null
                : combo.getValue().id();

        combo.setValue(null);
        combo.getItems().clear();

        if (task == null) {
            return;
        }

        try {
            List<TaskStep> steps = taskStepRepository.findByTaskId(task.getId());

            combo.getItems().setAll(steps);

            steps.stream()
                    .filter(s -> s.id().equals(previousId))
                    .findFirst()
                    .ifPresent(combo::setValue);

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void refreshSchedule(
            TimeBlockRepository repository,
            TimelineView timeline,
            VBox blockList,
            LocalDate date) throws SQLException {

        List<TimeBlock> blocks = repository.findForDate(date);
        List<Task> availableTasks = new TaskRepository().findAll();

        Map<String, String> taskColors = new HashMap<>();

        for (TimeBlock block : blocks) {

            if (block.type() != TimeBlock.Type.TASK
                    || block.taskId() == null) {
                continue;
            }

            String category = categoryRepository.findForTask(
                    block.taskId());

            taskColors.put(
                    block.taskId(),
                    categoryColors.getOrDefault(category, "#DCEAFE"));
        }

        timeline.setTaskColors(taskColors);
        timeline.showDate(date, blocks);

        blockList.getChildren().clear();

        for (TimeBlock block : blocks) {

            String timeText = block.start().toLocalTime()
                    + "–"
                    + block.end().toLocalTime();

            if (!block.start().toLocalDate()
                    .equals(block.end().toLocalDate())) {

                timeText += " (+next day)";
            }

            Label label = new Label(
                    timeText + "  ·  " + block.title());

            if (block.taskId() != null) {
                label.setText(label.getText() + "  · linked");
            }

            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setTextOverrun(
                    javafx.scene.control.OverrunStyle.ELLIPSIS);

            label.setTooltip(new javafx.scene.control.Tooltip(
                    block.title() + "\n"
                            + block.start() + " → " + block.end()));

            HBox.setHgrow(label, Priority.ALWAYS);

            Button editButton = new Button("Edit");
            Button deleteButton = new Button("×");

            HBox row = new HBox(8, label, editButton, deleteButton);

            TextField titleEdit = new TextField(block.title());

            DatePicker startDateEdit = new DatePicker(block.start().toLocalDate());

            DatePicker endDateEdit = new DatePicker(block.end().toLocalDate());

            TextField startTimeEdit = new TextField(block.start().toLocalTime().toString());

            TextField endTimeEdit = new TextField(block.end().toLocalTime().toString());

            startTimeEdit.setPrefColumnCount(5);
            endTimeEdit.setPrefColumnCount(5);

            ComboBox<TimeBlock.Type> typeEdit = new ComboBox<>();
            typeEdit.getItems().addAll(TimeBlock.Type.values());
            typeEdit.setValue(block.type());

            ComboBox<Task> linkedTaskEdit = new ComboBox<>();
            linkedTaskEdit.getItems().setAll(availableTasks);
            linkedTaskEdit.setPromptText("No linked task");
            linkedTaskEdit.setPrefWidth(240);

            for (Task task : availableTasks) {
                if (task.getId().equals(block.taskId())) {
                    linkedTaskEdit.setValue(task);
                    break;
                }
            }

            HBox linkedEditRow = new HBox(
                    8,
                    new Label("Linked task"),
                    linkedTaskEdit);

            ComboBox<TaskStep> linkedStepEdit = createStepCombo();

            linkedTaskEdit.valueProperty().addListener(
                    (obs, oldTask, newTask) -> reloadTaskSteps(linkedStepEdit, newTask));

            reloadTaskSteps(linkedStepEdit, linkedTaskEdit.getValue());

            if (block.stepId() != null) {
                linkedStepEdit.getItems().stream()
                        .filter(step -> step.id().equals(block.stepId()))
                        .findFirst()
                        .ifPresent(linkedStepEdit::setValue);
            }

            linkedStepEdit.setOnShowing(event -> reloadTaskSteps(linkedStepEdit, linkedTaskEdit.getValue()));

            Button clearEditStep = new Button("Whole task");
            clearEditStep.setOnAction(event -> linkedStepEdit.setValue(null));

            HBox linkedStepEditRow = new HBox(
                    8, new Label("Linked step"),
                    linkedStepEdit, clearEditStep);

            linkedStepEditRow.visibleProperty().bind(
                    typeEdit.valueProperty()
                            .isEqualTo(TimeBlock.Type.TASK)
                            .and(linkedTaskEdit.valueProperty().isNotNull()));

            linkedStepEditRow.managedProperty().bind(
                    linkedStepEditRow.visibleProperty());

            linkedEditRow.visibleProperty().bind(
                    typeEdit.valueProperty().isEqualTo(TimeBlock.Type.TASK));

            linkedEditRow.managedProperty().bind(
                    linkedEditRow.visibleProperty());

            Button saveEditButton = new Button("Save");
            Button cancelEditButton = new Button("Cancel");

            Label editStatus = new Label();

            VBox editor = new VBox(
                    6,
                    titleEdit,
                    new HBox(8, new Label("Start"), startDateEdit, startTimeEdit),
                    new HBox(8, new Label("End"), endDateEdit, endTimeEdit),
                    linkedEditRow,
                    linkedStepEditRow,
                    new HBox(8, typeEdit, saveEditButton, cancelEditButton),
                    editStatus);

            editor.setVisible(false);
            editor.setManaged(false);

            editButton.setOnAction(event -> {
                boolean show = !editor.isVisible();
                editor.setVisible(show);
                editor.setManaged(show);
            });

            cancelEditButton.setOnAction(event -> {
                editor.setVisible(false);
                editor.setManaged(false);
            });

            saveEditButton.setOnAction(event -> {

                String newTitle = titleEdit.getText().trim();

                if (newTitle.isEmpty()
                        || startDateEdit.getValue() == null
                        || endDateEdit.getValue() == null) {

                    editStatus.setText("Title and dates are required.");
                    return;
                }

                try {
                    LocalDateTime newStart = LocalDateTime.of(
                            startDateEdit.getValue(),
                            LocalTime.parse(startTimeEdit.getText().trim()));

                    LocalDateTime newEnd = LocalDateTime.of(
                            endDateEdit.getValue(),
                            LocalTime.parse(endTimeEdit.getText().trim()));

                    if (!newEnd.isAfter(newStart)) {
                        editStatus.setText("End must be after start.");
                        return;
                    }

                    String editTaskId = typeEdit.getValue() == TimeBlock.Type.TASK
                            && linkedTaskEdit.getValue() != null
                                    ? linkedTaskEdit.getValue().getId()
                                    : null;

                    String editStepId = editTaskId != null
                            && linkedStepEdit.getValue() != null
                                    ? linkedStepEdit.getValue().id()
                                    : null;

                    TimeBlock updatedBlock = new TimeBlock(
                            block.id(),
                            newTitle,
                            newStart,
                            newEnd,
                            typeEdit.getValue(),
                            editTaskId,
                            editStepId);

                    repository.update(updatedBlock);

                    refreshSchedule(
                            repository, timeline, blockList, date);

                } catch (DateTimeParseException ex) {
                    editStatus.setText("Invalid time. Use HH:mm.");
                } catch (SQLException ex) {
                    editStatus.setText("Database update failed.");
                    ex.printStackTrace();
                }
            });

            deleteButton.setOnAction(event -> {
                try {
                    repository.delete(block.id());

                    refreshSchedule(
                            repository, timeline, blockList, date);

                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            });

            VBox entry = new VBox(4, row, editor);
            blockList.getChildren().add(entry);
        }
    }

    private boolean matchesFilter(Task task, String filter) {

        LocalDate date = task.getPlannedDate();
        LocalDate today = LocalDate.now();

        return switch (filter) {
            case "Today" -> today.equals(date);
            case "Tomorrow" -> today.plusDays(1).equals(date);
            case "Backlog" -> date == null;
            case "Replan" -> date != null && date.isBefore(today) && !task.isCompleted();
            case "Earlier" -> date != null && date.isBefore(today);
            case "All" -> true;
            default -> false;
        };
    }

    private void refreshTaskList(
            List<Task> tasks, VBox taskList, Label progressLabel, ProgressBar progressBar, TaskRepository repository,
            String filter) {

        taskList.getChildren().clear();

        for (Task task : tasks) {
            if (!matchesFilter(task, filter)) {
                continue;
            }

            VBox taskEntry = createTaskRow(task, tasks, taskList, progressLabel, progressBar, repository, filter);

            taskList.getChildren().add(taskEntry);
        }

        updateProgress(tasks, progressLabel, progressBar, filter);
    }

    private VBox createTaskRow(
            Task task,
            List<Task> tasks,
            VBox taskList,
            Label progressLabel,
            ProgressBar progressBar,
            TaskRepository repository, String filter) {

        CheckBox checkBox = new CheckBox(task.getTitle());

        checkBox.getStyleClass().add("task-title");

        checkBox.setSelected(task.isCompleted());

        Label durationLabel = new Label(task.getEstimatedMinutes() + " min");

        DatePicker taskDateInput = new DatePicker(task.getPlannedDate());
        taskDateInput.setPrefWidth(150);

        Button clearDateButton = new Button("Backlog");

        Button scheduleTaskButton = new Button("Schedule");

        Button startTaskFocusButton = new Button("Focus");

        startTaskFocusButton.setOnAction(event -> {
            if (focusRequest != null) {
                focusRequest.accept(task, null);
            }
        });

        scheduleTaskButton.setOnAction(event -> {
            if (scheduleRequest != null) {
                scheduleRequest.accept(task, null);
            }
        });

        Button deleteButton = new Button("x");

        HBox taskRow = new HBox(
                12, checkBox, durationLabel, taskDateInput, clearDateButton, deleteButton);

        String categoryName = "General";

        try {
            categoryName = categoryRepository.findForTask(
                    task.getId());
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        MenuButton categoryButton = new MenuButton(categoryName);
        categoryButton.setPrefWidth(110);

        String colorHex = categoryColors.getOrDefault(
                categoryName, "#E7ECF3");

        categoryButton.setStyle(
                "-fx-background-color: " + colorHex + ";" +
                        "-fx-background-radius: 9px;");

        for (String name : categoryColors.keySet()) {

            MenuItem item = new MenuItem(name);

            item.setOnAction(event -> {

                try {
                    categoryRepository.assign(
                            task.getId(), name);

                    refreshTaskList(
                            tasks, taskList,
                            progressLabel, progressBar,
                            repository, filter);

                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            });

            categoryButton.getItems().add(item);
        }

        taskRow.getChildren().add(1, categoryButton);

        checkBox.setWrapText(true);
        checkBox.setMinWidth(0);
        checkBox.setMaxWidth(Double.MAX_VALUE);

        HBox titleLine = new HBox(
                12, checkBox, categoryButton);

        titleLine.setAlignment(Pos.CENTER_LEFT);

        checkBox.setMinWidth(0);
        checkBox.setWrapText(true);

        HBox.setHgrow(checkBox, Priority.ALWAYS);

        categoryButton.setMinWidth(Region.USE_PREF_SIZE);
        categoryButton.setMaxWidth(Region.USE_PREF_SIZE);

        HBox.setHgrow(checkBox, Priority.ALWAYS);

        FlowPane metadataLine = new FlowPane(
                10, 6,
                durationLabel,
                taskDateInput,
                clearDateButton,
                scheduleTaskButton,
                deleteButton);

        Button editTaskButton = new Button("Edit");

        metadataLine.getChildren().add(
                metadataLine.getChildren().size() - 1,
                editTaskButton);

        VBox rowContent = new VBox(
                8, titleLine, metadataLine);

        rowContent.setMaxWidth(Double.MAX_VALUE);

        // Keep metadata separate from actions.
        metadataLine.getChildren().removeAll(
                scheduleTaskButton,
                editTaskButton,
                deleteButton);

        metadataLine.setAlignment(Pos.CENTER_LEFT);

        // Reusable right-aligned action row.
        HBox taskActions = UiLayout.rightActions(
                scheduleTaskButton,
                startTaskFocusButton,
                editTaskButton,
                deleteButton);

        taskActions.getStyleClass().add("task-actions");

        rowContent.getChildren().add(taskActions);

        taskRow.getChildren().setAll(rowContent);
        HBox.setHgrow(rowContent, Priority.ALWAYS);

        taskRow.getStyleClass().add("task-row");

        taskDateInput.valueProperty().addListener((observable, oldDate, newDate) -> {

            if (Objects.equals(task.getPlannedDate(), newDate)) {
                return;
            }

            try {
                repository.updatePlannedDate(task.getId(), newDate);

                task.setPlannedDate(newDate);

                Platform.runLater(() -> refreshTaskList(
                        tasks, taskList, progressLabel, progressBar, repository, filter));
            } catch (SQLException ex) {
                ex.printStackTrace();

                taskDateInput.setValue(task.getPlannedDate());
            }
        });

        clearDateButton.setOnAction(e -> taskDateInput.setValue(null));

        TaskStepsView stepsView = new TaskStepsView(
                task,
                checkBox,
                repository,
                () -> {
                    updateProgress(
                            tasks, progressLabel, progressBar, filter);

                    if ("Replan".equals(filter) && task.isCompleted()) {
                        Platform.runLater(() -> refreshTaskList(
                                tasks, taskList, progressLabel,
                                progressBar, repository, filter));
                    }
                },
                step -> {
                    if (scheduleRequest != null) {
                        scheduleRequest.accept(task, step);
                    }
                },
                step -> {
                    if (focusRequest != null) {
                        focusRequest.accept(task, step);
                    }
                });

        TextField taskTitleEdit = new TextField(task.getTitle());

        Spinner<Integer> taskMinutesEdit = new Spinner<>(
                1, 1440, task.getEstimatedMinutes(), 5);

        taskMinutesEdit.setPrefWidth(105);

        Button saveTaskEdit = new Button("Save");
        Button cancelTaskEdit = new Button("Cancel");

        Label taskEditStatus = new Label();

        FlowPane taskEditActions = new FlowPane(
                8, 6,
                new Label("Estimated minutes"),
                taskMinutesEdit,
                saveTaskEdit,
                cancelTaskEdit);

        VBox taskEditor = new VBox(
                8, taskTitleEdit, taskEditActions, taskEditStatus);

        taskEditor.setVisible(false);
        taskEditor.setManaged(false);

        editTaskButton.setOnAction(event -> {
            taskEditor.setVisible(true);
            taskEditor.setManaged(true);
        });

        cancelTaskEdit.setOnAction(event -> {
            taskEditor.setVisible(false);
            taskEditor.setManaged(false);
        });

        saveTaskEdit.setOnAction(event -> {

            String title = taskTitleEdit.getText().trim();
            int minutes = taskMinutesEdit.getValue();

            if (title.isEmpty()) {
                taskEditStatus.setText("Task title is required.");
                return;
            }

            try {
                repository.updateDetails(task.getId(), title, minutes);

                task.setTitle(title);
                task.setEstimatedMinutes(minutes);

                checkBox.setText(title);
                durationLabel.setText(minutes + " min");

                stepsView.refresh();

                updateProgress(
                        tasks, progressLabel, progressBar, filter);

                taskEditor.setVisible(false);
                taskEditor.setManaged(false);

            } catch (SQLException ex) {
                taskEditStatus.setText("Unable to update task.");
                ex.printStackTrace();
            }
        });

        VBox taskEntry = new VBox(
                6, taskRow, taskEditor, stepsView);

        checkBox.setOnAction(e -> {

            boolean completed = checkBox.isSelected();

            try {
                repository.updateCompleted(task.getId(), completed);

                task.setCompleted(completed);
                stepsView.refresh();
                if ("Replan".equals(filter)) {
                    Platform.runLater(
                            () -> refreshTaskList(tasks, taskList, progressLabel, progressBar, repository, filter));
                } else {
                    updateProgress(tasks, progressLabel, progressBar, filter);
                }
            } catch (SQLException ex) {
                checkBox.setSelected(task.isCompleted());
                ex.printStackTrace();
            }
        });

        deleteButton.setOnAction(e -> {

            try {
                repository.delete(task.getId());

                tasks.remove(task);
                taskList.getChildren().remove(taskEntry);

                updateProgress(tasks, progressLabel, progressBar, filter);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        return taskEntry;
    }

    private int getTaskCompletedMinutes(Task task) {

        try {
            List<TaskStep> steps = taskStepRepository.findByTaskId(task.getId());

            if (steps.isEmpty()) {
                return task.isCompleted()
                        ? task.getEstimatedMinutes()
                        : 0;
            }

            return steps.stream()
                    .filter(TaskStep::completed)
                    .mapToInt(TaskStep::estimatedMinutes)
                    .sum();

        } catch (SQLException ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    private int getTaskPlannedMinutes(Task task) {

        try {
            List<TaskStep> steps = taskStepRepository.findByTaskId(task.getId());

            if (steps.isEmpty()) {
                return task.getEstimatedMinutes();
            }

            int allocated = steps.stream()
                    .mapToInt(TaskStep::estimatedMinutes)
                    .sum();

            return Math.max(task.getEstimatedMinutes(), allocated);

        } catch (SQLException ex) {
            ex.printStackTrace();
            return task.getEstimatedMinutes();
        }
    }

    private boolean isTaskActuallyCompleted(Task task) {

        int planned = getTaskPlannedMinutes(task);
        int completed = getTaskCompletedMinutes(task);

        return completed >= planned && planned > 0;
    }

    private void updateProgress(List<Task> tasks, Label progressLabel, ProgressBar progressBar, String filter) {

        int completedTasks = 0;
        int totalTasks = 0;

        int plannedMinutes = 0;
        int completedMinutes = 0;
        int remainingMinutes = 0;

        for (Task task : tasks) {

            if (!matchesFilter(task, "Today")) {
                continue;
            }

            totalTasks++;

            int taskPlanned = getTaskPlannedMinutes(task);
            int taskCompleted = getTaskCompletedMinutes(task);

            plannedMinutes += taskPlanned;
            completedMinutes += taskCompleted;
            remainingMinutes += Math.max(0, taskPlanned - taskCompleted);

            if (isTaskActuallyCompleted(task)) {
                completedTasks++;
            }
        }

        double progress = plannedMinutes == 0
                ? 0.0
                : (double) completedMinutes / plannedMinutes;

        progressLabel.setText(
                "Today: " + Math.round(progress * 100) + "%"
                        + "  ·  " + completedMinutes + "/" + plannedMinutes
                        + " min");

        dailyProgressDetails.setText(
                remainingMinutes + " min remaining"
                        + "  ·  " + completedTasks + "/" + totalTasks
                        + " tasks completed");

        progressBar.setProgress(progress);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void showFocusMessage(Stage owner, String message) {

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);

        alert.initOwner(owner);
        alert.setTitle("Focus");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }
}
