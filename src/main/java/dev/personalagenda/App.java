package dev.personalagenda;

import dev.personalagenda.model.Task;
import dev.personalagenda.model.TimeBlock;
import dev.personalagenda.data.Database;
import dev.personalagenda.data.TaskRepository;
import dev.personalagenda.data.TimeBlockRepository;
import dev.personalagenda.view.TimelineView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
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
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Database.initialize();
        TaskRepository repository = new TaskRepository();

        System.out.println(
                "Saved tasks to database: " + repository.findAll().size());

        List<Task> tasks = repository.findAll();

        Label title = new Label("Personal Agenda");

        TextField taskInput = new TextField();
        taskInput.setPromptText("Enter a task: ");

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
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            tasks.add(newTask);

            if (matchesFilter(newTask, viewFilter.getValue())) {
                HBox taskRow = createTaskRow(
                        newTask, tasks, taskList, progressLabel, progressBar, repository, viewFilter.getValue());

                taskList.getChildren().add(taskRow);
            } else {
                viewFilter.setValue("All");
            }

            taskInput.clear();

            updateProgress(tasks, progressLabel, progressBar, viewFilter.getValue());
        });

        taskInput.setOnAction(event -> addButton.fire());

        TimelineView timeline = new TimelineView();

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

        CheckBox nextDayInput = new CheckBox("Ends next day");

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

                TimeBlock block = new TimeBlock(
                        titleText,
                        start,
                        end,
                        blockTypeInput.getValue(),
                        null);

                blockRepository.insert(block);

                refreshSchedule(blockRepository, timeline, scheduledBlocks, today);

                blockTitleInput.clear();
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

        HBox dateRow = new HBox(
                10, new Label("Planned date:"), plannedDateInput, backlogButton);

        HBox timeInputRow = new HBox(
                8,
                new Label("Start"), startTimeInput,
                new Label("End"), endTimeInput,
                nextDayInput);

        HBox blockInputRow = new HBox(
                8, blockTitleInput, blockTypeInput, saveBlockButton);

        VBox scheduleEditor = new VBox(
                8, scheduleHeader, timeInputRow,
                blockInputRow, blockStatus, new Label("Scheduled Activities"), scheduledBlocks);

        VBox mainColumn = new VBox(16, title, inputRow, dateRow, new Label("View: "), viewFilter, taskList,
                progressLabel, progressBar, scheduleEditor);

        ScrollPane checklistPane = new ScrollPane(mainColumn);
        checklistPane.setFitToWidth(true);
        checklistPane.setMinWidth(400);

        SplitPane layout = new SplitPane(
                checklistPane,
                timeline);

        layout.setDividerPositions(0.62);
        layout.setPadding(new Insets(20));
        layout.getStyleClass().add("app-root");
        mainColumn.getStyleClass().add("content-card");
        timeline.getStyleClass().add("timeline-card");

        Scene scene = new Scene(layout, 800, 640);

        scene.getStylesheets().add(
                getClass().getResource("/styles/light.css").toExternalForm());

        stage.setTitle("Personal Agenda");
        stage.setScene(scene);
        stage.show();
    }

    private void refreshSchedule(
            TimeBlockRepository repository,
            TimelineView timeline,
            VBox blockList,
            LocalDate date) throws SQLException {

        List<TimeBlock> blocks = repository.findForDate(date);

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

            Button saveEditButton = new Button("Save");
            Button cancelEditButton = new Button("Cancel");

            Label editStatus = new Label();

            VBox editor = new VBox(
                    6,
                    titleEdit,
                    new HBox(8, new Label("Start"), startDateEdit, startTimeEdit),
                    new HBox(8, new Label("End"), endDateEdit, endTimeEdit),
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
                            block.taskId());

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

            HBox row = createTaskRow(task, tasks, taskList, progressLabel, progressBar, repository, filter);

            taskList.getChildren().add(row);
        }

        updateProgress(tasks, progressLabel, progressBar, filter);
    }

    private HBox createTaskRow(
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

        checkBox.setOnAction(e -> {

            boolean completed = checkBox.isSelected();

            try {
                repository.updateCompleted(task.getId(), completed);

                task.setCompleted(completed);
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
                taskList.getChildren().remove(taskRow);

                updateProgress(tasks, progressLabel, progressBar, filter);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        return taskRow;
    }

    private void updateProgress(List<Task> tasks, Label progressLabel, ProgressBar progressBar, String filter) {

        int completed = 0;
        int total = 0;
        int plannedMinutes = 0;
        int remainingMinutes = 0;

        for (Task task : tasks) {

            if (!matchesFilter(task, filter)) {
                continue;
            }

            total++;
            plannedMinutes += task.getEstimatedMinutes();

            if (task.isCompleted()) {
                completed++;
            } else {
                remainingMinutes += task.getEstimatedMinutes();
            }
        }

        double progress = total == 0
                ? 0.0
                : (double) completed / total;

        progressLabel.setText(
                filter + ": " + completed + "/" + total + " completed | Planned: " + plannedMinutes
                        + " min | Remaining: "
                        + remainingMinutes + " min");

        progressBar.setProgress(progress);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
