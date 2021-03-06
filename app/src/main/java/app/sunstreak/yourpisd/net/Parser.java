package app.sunstreak.yourpisd.net;

import android.support.annotation.NonNull;
import app.sunstreak.yourpisd.TermFinder;
import app.sunstreak.yourpisd.net.data.*;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class Parser {

    // Cannot be instantiated.
    private Parser() {
    }

    /**
     * Displays a grade based on what the grade is. If the grade is -1, nothing is printed
     * If the grade is -2, an "X" is printed.
     *
     * @param grade the grade to print out
     * @param decimal whether to print out 3 decimal places.
     * @return the grade as a string
     */
    public static String gradeToString(double grade, boolean decimal)
    {
        if (grade == -1)
            return "";
        else if (grade == -2)
            return "X";
        else if (decimal)
            return String.format("%.3f", grade);
        else
            return String.format("%.0f", grade);
    }

    /**
     * Parses the term report (assignments for a class during a grading period).
     *
     * @param html text of detailed report
     * @param report the TermReport object to store the data into.
     */
    @NonNull
    public static void parseTermReport(String html, TermReport report) {
        if (html == null) {
            System.err.println("No html for parsing detailed grade summary");
            return;
        }
        Document doc = Jsoup.parse(html);
        if (doc == null)
            return;
        Element main = doc.getElementById("Main").getElementById("Content").getElementById("ContentMain");

        //refill grade categories and assignments
        List<GradeCategory> categories = report.getCategories();
        categories.clear();
        report.getAssignments().clear();

        Element categoryTable = main.getElementById("Categories");
        if (categoryTable != null && categoryTable.children().size() > 0) {
            //For each <TR> element
            for (Element category : categoryTable.children().get(0).children()) {
                categories.add(new GradeCategory(category.getElementsByClass("description").get(0).html().split("\n")[0].trim(), Integer.parseInt(category.getElementsByClass("percent").get(0).html().replaceAll("[^0-9]", "")) * 0.01));
                categories.get(categories.size() - 1).setGrade(Integer.parseInt(category.getElementsByClass("letter").get(1).child(0).child(0).child(0).html().replace("%", "")));
                //Log.d("testTag", report.getCategories().get(report.getCategories().size() - 1).getGrade() + " " + report.getCategories().get(report.getCategories().size() - 1).getWeight() + " " + report.getCategories().get(report.getCategories().size() - 1).getType());
            }
        }

        GradeCategory noCategory = new GradeCategory(GradeCategory.NO_CATEGORY, 0);
        categories.add(noCategory); //Put no-category grades at the end (if there are any).

        Element assignments = main.getElementById("Assignments");
        if (assignments != null && assignments.children().size() > 0)
        {
            //an assignment doesn't have to have a due date. If it doesn't, use the last parsed due date
            int month = 1;
            int day = 1;

            //for each assignment
            for (Element assignment : assignments.children().get(0).children())
            {
                String name = org.jsoup.parser.Parser.unescapeEntities(assignment.getElementsByClass("title").get(0).html().replaceAll("&amp;", "&"), true);

                Elements divCategory = assignment.getElementsByClass("category");
                GradeCategory category = noCategory;
                if (!divCategory.isEmpty())
                {
                    String catName = divCategory.get(0).html();
                    for (GradeCategory cc : categories)
                    {
                        if (cc.getType().equals(catName))
                        {
                            category = cc;
                            break;
                        }
                    }
                }

                final int year = 2016; //TODO: year of fall semester
                final String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

                Elements spanDate = assignment.getElementsByClass("m");
                if (!spanDate.isEmpty())
                {
                    month = Arrays.asList(months).indexOf(spanDate.get(0).html()) + 1;
                    day = Integer.parseInt(spanDate.get(0).parent().html().replaceAll("[^0-9]", ""));
                }
                DateTime date = new DateTime(year, month, day, 0, 0);
                if (date.getMonthOfYear() < 7) //spring semester
                    date.plusYears(1);

                Assignment newAssignment = new Assignment(name, category, date);
                report.getAssignments().add(newAssignment);

                Elements weight = assignment.getElementsByClass("weight");
                if (weight.isEmpty())
                    newAssignment.setWeight(1);
                else
                    newAssignment.setWeight(Double.parseDouble(weight.get(0).html().replaceAll("[^0-9]", "")));

                String temp = assignment.getElementsByClass("points").get(0).html();
                try
                {
                    if (temp.equalsIgnoreCase("X"))
                        newAssignment.setGrade(-2);
                    else if (temp.equalsIgnoreCase("Z"))
                        newAssignment.setGrade(0);
                    else if (temp.isEmpty())
                        newAssignment.setGrade(-1);
                    else
                        newAssignment.setGrade(Double.parseDouble(temp));
                }
                catch (NumberFormatException e)
                {
                    newAssignment.setGrade(-1);
                }

                try
                {
                    newAssignment.setMaxGrade(Double.parseDouble(assignment.getElementsByClass("max").get(0).html()));
                }
                catch (NumberFormatException e)
                {
                    newAssignment.setMaxGrade(0);
                }

                //Log.d("testTag", "\n" + name + " === " + "\n");
            }
        }
    }

    /** Parses average of each term from GradeSummary.aspx.
     *
     * @param html the html body of GradeSummary.aspx
     * @param classes the mappings of class (enrollment) ID to class reports. Parses grades into here.
     */
    @NonNull
    public static void parseGradeSummary(String html, Map<Integer, ClassReport> classes) {
        if (html == null) {
            System.err.println("No html for parsing grade summary");
            return;
        }
        Document doc = Jsoup.parse(html);
        if (doc == null)
            return;
        Element main = doc.getElementById("Main").getElementById("Content");

        // TODO: test next year
        Elements years = main.getElementById("ContentMain").getElementsByClass("calendar");

        // For each terms
        int maxTermNum = 0;
        Elements terms = years.get(0).getElementsByClass("term");
        for (Element term : terms) {
            Elements termName = term.getElementsByTag("h2");
            if (termName.isEmpty())
                continue;

            //Search term name in the list of terms
            String tempDate = termName.get(0).getElementsByClass("term").html();
            int termNum = 0;
            boolean isExam = false;
            for (TermFinder.Term t : TermFinder.Term.values()) {
                if (t.name.equalsIgnoreCase(tempDate)) {
                    termNum = t.ordinal();
                    isExam = t.isExam();
                    break;
                }
            }
            maxTermNum = Math.max(maxTermNum, termNum);

            // For each course
            Elements courses = term.children().get(1).children().get(0).children();
            for (Element course : courses) {
                Elements courseMain = course.getElementsByTag("tr").get(0).children();

                // Period number
                String period = courseMain.get(0).html();
                if (period.isEmpty())
                    period = "0";

                // Course name
                Element courseInfo = courseMain.get(1).children().get(0).children().get(0);
                String name = courseInfo.html();
                String query = courseInfo.attr("href").split("\\?", 2)[1];
                String[] parts = query.split("[&=]");

                //Parse course and term id
                int courseID = -1;
                int termID = -1;
                for (int i = 0; i < parts.length - 1; i += 2) {
                    if (parts[i].equalsIgnoreCase("Enrollment"))
                        courseID = Integer.parseInt(parts[i + 1]);
                    if (parts[i].equalsIgnoreCase("Term"))
                        termID = Integer.parseInt(parts[i + 1]);
                }

                //Add new class report as needed.
                ClassReport report;
                if (classes.containsKey(courseID))
                {
                    report = classes.get(courseID);
                    report.setCourseName(name);
                }
                else{
                    report = new ClassReport(courseID, name);
                    classes.put(courseID, report);
                }

                String teacher = courseMain.get(1).children().get(1).getElementsByClass("teacher").get(0).html();
                report.setPeriodNum(Integer.parseInt(period));
                report.setTeacherName(teacher);

                //Create a new term as needed.
                TermReport termReport = report.getTerm(termNum);
                if (termReport == null)
                {
                    termReport = new TermReport(report, termID, isExam);
                    report.setTerm(termNum, termReport);
                    classes.put(report.getClassID(), report);
                }

                // Course grade for a term (might be empty string if no grade) - formatted as number + %
                int grade;
                if (courseMain.get(2).children().isEmpty())
                    grade = -1; //No grade exists.
                else {
                    String gradeString = courseMain.get(2).children().get(0).children().get(0).children().get(0).html();
                    try
                    {
                        grade = Integer.parseInt(gradeString.substring(0, gradeString.length() - 1));
                    }
                    catch (NumberFormatException e)
                    {
                        grade = -1;
                    }
                }
                termReport.setGrade(grade);
            }
        }

        TermFinder.setCurrentTermIndex(maxTermNum);
    }


    /**
     * Parses and returns a list of students' informations (name and INTERNAL student id) from the Gradebook.
     *
     * @param sess the session loading the user.
     * @param html the source code for ANY page in Gradebook (usually GradeSummary.aspx)
     * @return the list of students
     */
    @NonNull
    public static List<Student> parseStudents(Session sess, String html) {
        if (html == null)
            return null;
        Document doc = Jsoup.parse(html);
        if (doc == null)
            return null;
        Element main = doc.getElementById("Main").getElementById("Navigation").getElementsByTag("li").get(0);

        //main.getElementById("ContentHeader").getElementsByClass("container").get(0).getElementsByTag("h2").get(0).html();

        //TODO multiple students
        ArrayList<Student> students = new ArrayList<>();
        Element singleID = doc.getElementById("ctl00_ctl00_ContentPlaceHolder_uxStudentId");
        Element singleImage = doc.getElementById("ctl00_ctl00_ContentPlaceHolder_uxStudentPhoto");
        String photoID = singleImage.attr("src").split("studentId=")[1];
        Student single = new Student(Integer.parseInt(singleID.attr("value")), photoID, main.html(), sess);
        students.add(single);
        return students;
    }


}
