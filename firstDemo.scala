//akka packages
import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

//scala packages
import scala.collection.mutable
import scala.io.Source
import scala.io.BufferedSource
import scala.util.control._
import scala.util.control.Breaks


//java package
import java.io._
import scala.io.BufferedSource

//graph package
//import scala.swing._  libraryDependencies += "org.scala-lang" % "scala-swing" % "2.10.4"
import java.io.File;
import java.io.IOException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


object GlobalDataBase{
    var Table:mutable.Map[String,Int] = mutable.Map()
}

//sample worker
object AddWorker{
    def apply() = Behaviors.setup(context => new AddWorker(context))
}

class AddWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var destination: ActorRef[Any] = _
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            val temp = msg.asInstanceOf[Tuple2[String,Any]]
            val tag = temp._1
            tag match
            {
                case "Setup" =>
                    val address = temp._2.asInstanceOf[ActorRef[Any]]
                    this.destination = address
                case "Package" =>
                    println("working in addworkers")
                    println(temp._2)
                    val info = temp._2.asInstanceOf[String]
                    val a = info.split(",")
                    val b = Tools.add1(a(0).toInt,a(1).toInt)
                    this.destination ! b
            }
            this
        }
}

object ReaderWorker{
    def apply() = Behaviors.setup(context => new ReaderWorker(context))
}

class ReaderWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var destination: ActorRef[Any] = _
    var targetElement: String = _
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            val temp = msg.asInstanceOf[Tuple2[String,Any]]
            val tag = temp._1
            tag match
            {
                case "Setup" =>
                    val info = temp._2.asInstanceOf[Tuple2[String,ActorRef[Any]]]
                    this.destination = info._2
                    this.targetElement = info._1
                case "Package" =>
                    println("working in readerworker")
                    var readtemp = fileReaders.readFile(this.targetElement)
                    var fileline = readtemp.getLines()
                    var filemessage = fileline.next()
                    do {
                        this.destination ! ("Package",filemessage)
                        filemessage = fileline.next()
                    } while(fileline.hasNext)
                    this.destination ! ("Package",filemessage) //last line
            }
            this
        }
}

object ResultWorker{
    def apply() = Behaviors.setup(context => new ResultWorker(context))
}

class ResultWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            println("working in result worker")
            println(msg)
            this
        }
}

object CompareWorker{
    def apply() = Behaviors.setup(context => new CompareWorker(context))
}

class CompareWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var destination: ActorRef[Any] = _
    var targetElement: String = _
    val mybreak = new Breaks;
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            val temp = msg.asInstanceOf[Tuple2[String,Any]]
            val tag = temp._1
            tag match
            {
                case "Setup" =>
                    val info = temp._2.asInstanceOf[Tuple2[String,ActorRef[Any]]]
                    this.destination = info._2
                    this.targetElement = info._1
                case "Package" =>
                    val message = temp._2.asInstanceOf[String]
                    var element = message.split(",")
                    mybreak.breakable{
                        for(i <- element){
                            if(i == this.targetElement)
                            {
                                this.destination ! ("Package", (this.targetElement, message))
                                println((this.targetElement, message))
                                mybreak.break
                            }
                        }
                    }
            }
            this
        }
}

object ColumSelectWorker{
    def apply() = Behaviors.setup(context => new ColumSelectWorker(context))
}

class ColumSelectWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var destination: ActorRef[Any] = _
    var colum: Int = _
    val mybreak = new Breaks;
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            val temp = msg.asInstanceOf[Tuple2[String,Any]]
            val tag = temp._1
            tag match
            {
                case "Setup" =>
                    val info = temp._2.asInstanceOf[Tuple2[String,ActorRef[Any]]]
                    this.destination = info._2
                    this.colum = info._1.toInt
                case "Package" =>
                    val message = temp._2.asInstanceOf[String]
                    var element = message.split(",")
                    this.destination ! ("Package", (element(this.colum), message))
            }
            this
        }
}

object StaticWorker{
    def apply() = Behaviors.setup(context => new StaticWorker(context))
}

class StaticWorker(context:ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var destination: ActorRef[Any] = _
    override def onMessage(msg: Any): Behavior[Any] = 
        {
            val temp = msg.asInstanceOf[Tuple2[String,Any]]
            val tag = temp._1
            tag match
            {
                case "Setup" =>
                    val address = temp._2.asInstanceOf[ActorRef[Any]]
                    this.destination = address
                case "Package" =>
                    println("working in Static Worker")
                    val message = temp._2.asInstanceOf[Tuple2[String,String]]
                    var element = message._2.split(",")
                    if(GlobalDataBase.Table.contains(message._1))
                    {
                        var Inttemp = GlobalDataBase.Table(message._1)
                        Inttemp += 1
                        GlobalDataBase.Table(message._1) = Inttemp
                    }
                    else{
                        GlobalDataBase.Table += (message._1 -> 1)
                    }
            }
            this
        }
}

object fileReaders{
    def readFile(address:String): BufferedSource={
        var target = Source.fromFile(address)
        target
    }
    def commend(file:BufferedSource,system:ActorSystem[Any]):Unit ={
        var temp = file.getLines()
        temp.foreach{
            code =>
                println(code)
                system ! code
        }
    }
}

object demoMain {
    def apply(): Behavior[Any] = 
        Behaviors.setup {
            context => new demoMain(context)
        }
}

class demoMain(context: ActorContext[Any]) extends AbstractBehavior[Any](context) {
    var Table:mutable.Map[String,mutable.ListBuffer[ActorRef[Any]]] = mutable.Map()
    var cache: ActorRef[Any] = _
    override def onMessage(msg: Any): Behavior[Any] =
    {
        val temp = msg.asInstanceOf[String]
        val message = temp.split(",")
        var code = message(0)
        code match 
            {
                case "start" =>
                    val resultworker = context.spawn(ResultWorker(),"resultworker")
                    if (this.Table.contains("resultworker"))
                    {
                        var listtemp = this.Table("resultworker")
                        listtemp += resultworker
                        this.Table("resultworker") = listtemp
                        this
                    }
                    else{
                        this.Table += ("resultworker" -> mutable.ListBuffer(resultworker))
                        this.cache = resultworker
                        this
                    }
                case "add" =>
                    val add1worker = context.spawn(AddWorker(),"addworker")
                    this.Table += ("addworker" -> mutable.ListBuffer(add1worker))
                    add1worker ! ("Setup",cache)
                    this.cache = add1worker
                    this
                case "read" =>
                    val readerWorker = context.spawn(ReaderWorker(),"readerworker")
                    this.Table += ("readerworker" -> mutable.ListBuffer(readerWorker))
                    readerWorker ! ("Setup",(message(1),cache))
                    this.cache = readerWorker
                    this
                case "go" =>
                    println(this.cache)
                    this.cache ! ("Package","project Start")
                    this
                case "compare" =>
                    val compareWorker = context.spawn(CompareWorker(),"compareWorker")
                    this.Table += ("readerworker" -> mutable.ListBuffer(compareWorker))
                    compareWorker ! ("Setup",(message(1),cache))
                    this.cache = compareWorker
                    this
                case "static" =>
                    val staticworker = context.spawn(StaticWorker(),"staticworker")
                    if (this.Table.contains("staticworker"))
                    {
                        var listtemp = this.Table("staticworker")
                        listtemp += staticworker
                        staticworker ! ("Setup",cache)
                        this.Table("staticworker") = listtemp
                        this
                    }
                    else{
                        this.Table += ("staticworker" -> mutable.ListBuffer(staticworker))
                        staticworker ! ("Setup",cache)
                        this.cache = staticworker
                        this
                    }
                case "ColumSelect" =>
                    val columselectworker = context.spawn(ColumSelectWorker(),"columselectworker")
                    if (this.Table.contains("columselectworker"))
                    {
                        var listtemp = this.Table("columselectworker")
                        listtemp += columselectworker
                        columselectworker ! ("Setup",(message(1),cache))
                        this.Table("columselectworker") = listtemp
                        this
                    }
                    else{
                        this.Table += ("columselectworker" -> mutable.ListBuffer(columselectworker))
                        columselectworker ! ("Setup",(message(1),cache))
                        this.cache = columselectworker
                        this
                    }
            }
    }
}

object Tools{
    def add1(a: Int, b: Int): Int ={
        a + b
    }
}

object BarChartWorker{
    var data = new DefaultCategoryDataset
    def adddata(data:Int,name:String)={
        this.data.addValue(data,"amount",name)
    }

    var chart = ChartFactory.createBarChart(
        "demo", "", "amount",
        data, PlotOrientation.VERTICAL,
        false, true, false)
    def savecahrt(address:String)={
        var temp = new File(address)
        val chartPanel = new ChartPanel(this.chart)
        chartPanel.setSize(chartPanel.getPreferredSize())
        //chartPanel.doSaveAs()
        ChartUtils.saveChartAsPNG(temp,
            this.chart,
            chartPanel.getWidth(),
            chartPanel.getHeight())
    }

}

object firstDemo extends App{
    val testMain = ActorSystem(demoMain(),"testSystem")
    val file = fileReaders.readFile("demo.txt")
    fileReaders.commend(file,testMain)
    Thread.sleep(6000)
    //fileReaders.commend(file,testMain)
    //println(GlobalDataBase.Table.contains("North America"))
    //println(GlobalDataBase.Table("North America"))
    var test = GlobalDataBase.Table.values.toList
    var keys = GlobalDataBase.Table.keys.toList
    println(test)
    println(keys)
    for(i <- 0.to(test.length-1)){
        BarChartWorker.adddata(test(i),keys(i))
    }
    BarChartWorker.savecahrt("/home/aoran/Documents/scala pre/123.png")
}
