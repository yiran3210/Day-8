package dev.personalagenda;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.model.TaskStep;
import dev.personalagenda.data.Database;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TaskStepRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.data.CategoryRepository;
import dev.personalagenda.view.TimelineView;
import dev.personalagenda.view.TaskStepsView;
import dev.personalagenda.view.ProjectDashboard;
import dev.personalagenda.view.FloatingWidget;

// import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Separator;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.Scene;

public class App extends Application {

    private final CategoryRepository categoryRepository = new CategoryRepository();

    private final Map<String, String> categoryColors = new LinkedHashMap<>();

    private final TaskStepRepository taskStepRepository = new TaskStepRepository();

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
        progressBar.setPrefWidth(400);

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

        progressBar.setMinHeight(12);
        progressBar.setPrefHeight(12);
        progressBar.setPrefWidth(320);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.getStyleClass().add("daily-progress");

        VBox dailyProgressBox = new VBox(
                8, progressLabel, progressBar);

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

        Label scheduleTitle = new Label("Schedule");

        HBox scheduleHeader = new HBox(12, scheduleTitle, scheduleDateInput);

        TextField blockTitleInput = new TextField();
        blockTitleInput.setPromptText("Activity name");

        TextField startTimeInput = new TextField("16:00");
        TextField endTimeInput = new TextField("17:00");

        startTimeInput.setPrefColumnCount(5);
        endTimeInput.setPrefColumnCount(5);

        ComboBox<TimeBlock.Type> blockTypeInput = new ComboBox<>();
        blockTypeInput.getItems().addAll(TimeBlock.Type.values());
        blockTypeInput.setValue(TimeBlock.Type.TASK);

        ComboBox<Task> linkedTaskInput = new ComboBox<>();

        linkedTaskInput.setPromptText("Choose an existing task");
        linkedTaskInput.setPrefWidth(200);

        linkedTaskInput.setOnShowing(event -> {
            try {
                String selectedId = linkedTaskInput.getValue() == null
                        ? null
                        : linkedTaskInput.getValue().getId();

                linkedTaskInput.getItems().setAll(
                        repository.findAll());

                if (selectedId != null) {
                    linkedTaskInput.getItems().stream()
                            .filter(task -> selectedId.equals(task.getId()))
                            .findFirst()
                            .ifPresent(linkedTaskInput::setValue);
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        linkedTaskInput.valueProperty().addListener(
                (observable, oldTask, newTask) -> {
                    if (newTask != null) {
                        blockTitleInput.setText(newTask.getTitle());
                    }
                });

        HBox linkedTaskRow = new HBox(
                8,
                new Label("Linked task"),
                linkedTaskInput);

        linkedTaskRow.setVisible(true);
        linkedTaskRow.setManaged(true);

        blockTypeInput.valueProperty().addListener(
                (observable, oldType, newType) -> {

                    boolean isTask = newType == TimeBlock.Type.TASK;

                    linkedTaskRow.setVisible(isTask);
                    linkedTaskRow.setManaged(isTask);

                    if (!isTask) {
                        linkedTaskInput.setValue(null);
                    }
                });

        CheckBox nextDayInput = new CheckBox();
        Label nextDayLabel = new Label("Ends next day (+1 day)");

        HBox nextDayRow = new HBox(8, nextDayInput, nextDayLabel);

        Button saveBlockButton = new Button("Schedule");

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

                TimeBlock block = new TimeBlock(
                        titleText,
                        start,
                        end,
                        blockTypeInput.getValue(),
                        linkedTaskId);

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

        HBox dateRow = new HBox(
                10, new Label("Planned date:"), plannedDateInput, backlogButton);

        HBox timeInputRow = new HBox(
                8,
                new Label("Start"), startTimeInput,
                new Label("End"), endTimeInput);

        nextDayInput.setText("Ends next day (+1 day)");

        HBox blockInputRow = new HBox(
                8, blockTitleInput, blockTypeInput, saveBlockButton);

        VBox scheduleEditor = new VBox(
                8, scheduleHeader, timeInputRow, nextDayRow,
                blockInputRow, linkedTaskRow, blockStatus, new Label("Scheduled Activities"), scheduledBlocks);

        Label categoryTitle = new Label("Task Categories");

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

        VBox categoryEditor = new VBox(
                8,
                categoryTitle,
                new HBox(
                        8, colorCategoryInput,
                        categoryColorPicker,
                        saveCategoryButton),
                newCategoryInput,
                categoryStatus);

        Label quickAddTitle = new Label("Quick Add");
        Label taskListTitle = new Label("Task List");

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
                taskToolbar,
                taskList);

        VBox schedulePanel = new VBox(
                16,
                scheduleEditor);

        ProjectDashboard projectDashboard = new ProjectDashboard(categoryColors);

        VBox categoryPanel = new VBox(
                20,
                projectDashboard,
                new Separator(),
                categoryEditor);

        taskPanel.getStyleClass().add("content-card");
        schedulePanel.getStyleClass().add("content-card");
        categoryPanel.getStyleClass().add("content-card");

        ScrollPane taskScroll = new ScrollPane(taskPanel);
        taskScroll.setFitToWidth(true);

        ScrollPane scheduleScroll = new ScrollPane(schedulePanel);
        scheduleScroll.setFitToWidth(true);

        ScrollPane categoryScroll = new ScrollPane(categoryPanel);
        categoryScroll.setFitToWidth(true);

        Tab taskTab = new Tab("Tasks", taskScroll);
        taskTab.setClosable(false);

        Tab scheduleTab = new Tab("Schedule", scheduleScroll);
        scheduleTab.setClosable(false);

        Tab categoryTab = new Tab("Projects", categoryScroll);
        categoryTab.setOnSelectionChanged(event -> {
            if (categoryTab.isSelected()) {
                projectDashboard.refresh();
            }
        });
        categoryTab.setClosable(false);

        TabPane leftTabs = new TabPane(
                taskTab, scheduleTab, categoryTab);

        leftTabs.setMinWidth(420);

        SplitPane layout = new SplitPane(
                leftTabs,
                timeline);

        layout.setDividerPositions(0.62);
        layout.setPadding(new Insets(20));
        layout.getStyleClass().add("app-root");
        // mainColumn.getStyleClass().add("content-card");
        timeline.getStyleClass().add("timeline-card");

        Scene scene = new Scene(layout, 800, 640);

        scene.getStylesheets().add(
                getClass().getResource("/styles/light.css").toExternalForm());

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);

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
                });

        Button openWidgetButton = new Button("Open Mini Widget");
        openWidgetButton.setOnAction(
                event -> floatingWidget.show());

        timeline.getChildren().add(0, openWidgetButton);

        stage.show();
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
                    new HBox(8, new Label("End"), endDateEdit, endTimeEdit), linkedEditRow,
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

                    TimeBlock updatedBlock = new TimeBlock(
                            block.id(),
                            newTitle,
                            newStart,
                            newEnd,
                            typeEdit.getValue(),
                            typeEdit.getValue() == TimeBlock.Type.TASK
                                    && linkedTaskEdit.getValue() != null
                                            ? linkedTaskEdit.getValue().getId()
                                            : null);

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
            if (!matchesFilter(task, "Today")) {
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
        checkBox.setSelected(task.isCompleted());

        Label durationLabel = new Label(task.getEstimatedMinutes() + " min");

        DatePicker taskDateInput = new DatePicker(task.getPlannedDate());
        taskDateInput.setPrefWidth(150);

        Button clearDateButton = new Button("Backlog");

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

        HBox.setHgrow(checkBox, Priority.ALWAYS);

        FlowPane metadataLine = new FlowPane(
                10, 6,
                durationLabel,
                taskDateInput,
                clearDateButton,
                deleteButton);

        VBox rowContent = new VBox(
                8, titleLine, metadataLine);

        rowContent.setMaxWidth(Double.MAX_VALUE);

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
                });

        VBox taskEntry = new VBox(4, taskRow, stepsView);

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

            if (!matchesFilter(task, filter)) {
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
                        + " min\n"
                        + remainingMinutes + " min remaining"
                        + "  ·  " + completedTasks + "/" + totalTasks
                        + " tasks completed");

        progressBar.setProgress(progress);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
