# CodeDistillery  

## What?  

CodeDistillery is a framework aimed at facilitating the mining of source code changes from version control systems.

The thing that makes CodeDistillery a framework more than a tool, is its *support for pluggable source code mining mechanisms*, while providing the underlying infrastructure to efficiently apply these mechanisms on entire revision histories of numerous software repositories.

## Why?

While one could get away with mining a single repository with 100 or so commits without considering [scale](https://www.youtube.com/watch?v=b2F-DItXtZs), it is no longer the case when this task involves dozens and more of repositories, each having thousands and more of revisions. In fact, the latter is quite standard for current studies in empirical software engineering, so we thought that the tool we had built for this task could make life easier on other people trying to do similar stuff.

## How?

We utilize Spark as the distributed compute engine, and JGit as the data access layer to make the mining workload a highly parallel one. Then, we use spark to distribute and process it.

In light of the above, CodeDistillery would not have been possible (or at the very least, it would have been much much harder to build) without awesome people building awesome *open source* software, and in particular the OS projects we extensively built upon: [Apache Spark](https://spark.apache.org/), [JGit](https://www.eclipse.org/jgit/) and [ChangeDistiller](https://bitbucket.org/sealuzh/tools-changedistiller/wiki/Home).
  
## Getting Started

```bash  
git clone https://github.com/staslev/CodeDistillery  
cd CodeDistillery  
mvn clean install  
```

### Setting up Maven dependencies

  
```xml  
<dependencies>  
  <dependency> 
    <groupId>com.staslev.codedistillery</groupId>   
    <artifactId>distillery-core</artifactId>
    <version>0.5-SNAPSHOT</version>
  </dependency>
  <dependency>  
    <groupId>com.staslev.codedistillery</groupId>
    <artifactId>change-distiller-uzh</artifactId>
    <version>0.5-SNAPSHOT</version>
  </dependency>
</dependencies>  
```
### Usage

We demonstrate CodeDistillery by providing an out-of-the-box support for mining *Java fine-grained source code changes* from *Git repositories*. 

```scala  
object Main {  
  
  def main(args: Array[String]): Unit = {  
 
  val codeDistillery =  
    new CodeDistillery(
      vcsFactory = GitRepo.apply,  
      distillerFactory = UzhSourceCodeChangeDistiller.apply,  
      encoderFactory = () => UzhSourceCodeChangeCSVEncoder)  
    with CrossRepoRevisionParallelism
  
    val repoPath = Paths.get("/path/to/my/repo")  
    val output = Paths.get("/path/to/write/output")  
    val branch = "master"

    import LocalSparkParallelism.spark

    codeDistillery.distill(Set((repoPath, branch)), output)  
 }  
}
```

### Output

The output is a CSV file with a `#` delimiter, consisting of the following fields (in respective order):

 1. Project name
 2. Commit hash
 3. Author name
 4. Author email
 5. [Fine-grained change type](https://bitbucket.org/sealuzh/tools-changedistiller/src/feee5be3724a3eabfb7c415554cb26f2258a65f4/src/main/java/ch/uzh/ifi/seal/changedistiller/model/classifiers/ChangeType.java?at=master#lines-52:99)
 6. Unique name of changed entity
 7. [Significance level](https://bitbucket.org/sealuzh/tools-changedistiller/src/feee5be3724a3eabfb7c415554cb26f2258a65f4/src/main/java/ch/uzh/ifi/seal/changedistiller/model/classifiers/SignificanceLevel.java?at=master#lines-52:56)
 8. Parent [entity type](https://bitbucket.org/sealuzh/tools-changedistiller/src/feee5be3724a3eabfb7c415554cb26f2258a65f4/src/main/java/ch/uzh/ifi/seal/changedistiller/model/classifiers/java/JavaEntityType.java?at=master#lines-31:104)
 9. Unique name of parent entity
 10. Root entity type
 11. Unique name of root entity
 12. Commit message
 13. Filename

### Obtaining commit level datasets

The output from the previous stage is a dataset of raw fine-grained source code changes as distilled from a software repository. It is often useful to aggregate this raw dataset into commit level statistics. A commit level dataset can be obtained by performing the following:

```scala
val input1 :: input2 :: output :: Nil =  
  List("/path/to/input1", "/path/to/input2", "/path/to/output")  
 .map(Paths.get(_))

import LocalSparkParallelism.spark

PerCommit.aggregate(Set(input1, input2), output)
```

The output is a CSV file with a `#` delimiter, consisting of the following fields (in respective order):

 1. Project name
 2. Commit hash
 3. Author name
 4. Author mail
 5. Date
 6. Non test versatility
 7. Commit message
 8. Test cases added
 9. Test cases removed
 10. Test cases changed
 11. Test suites added
 12. Test suites removed
 13. Test suites affected
 14. Has issue ref
 15. Non test files in commit
 16. Total files in commit
 17. Commit message length
 
     ++ { fine-grained source code change type frequencies }
    Which is a lexicographically sorted list of fine-grained source code change types. 

The complete list of columns (a.k.a. header line) can be obtained using: `PerCommit.headerLine`.
