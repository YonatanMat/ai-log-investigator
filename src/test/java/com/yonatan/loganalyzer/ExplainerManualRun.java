package com.yonatan.loganalyzer;

import com.yonatan.loganalyzer.service.ErrorExplainerService;

/**
 * Manual check for Step 4b: asks Claude to explain one error message.
 *
 * Run it from IntelliJ with the green arrow next to main. It makes a real API
 * call, so it uses your key and a tiny amount of balance.
 *
 * The name does not end in "Test", so Maven's "mvn test" does not run it.
 * Automated tests should never make paid calls.
 */
public class ExplainerManualRun {

    public static void main(String[] args) {
        ErrorExplainerService explainer = new ErrorExplainerService();
        String answer = explainer.explain("Database connection failed");
        System.out.println(answer);
    }
}
