(ns selmer.core-test
  (:require [clojure.test :refer :all]
            [cheshire.core :refer :all]
            [selmer.tags :refer :all]
            [selmer.filters :refer :all]
            [selmer.parser :refer :all]
            [selmer.template-parser :refer :all]
            [selmer.util :refer :all])
  (:import java.util.Locale
           java.io.File))

(def path (str "test/templates" File/separator))

(defn fix-line-sep [s] (clojure.string/replace s "\n" (System/lineSeparator)))

(deftest dev-error-handling
  (is (= "No filter defined with the name 'woot'"
         (try (render "{{blah|safe|woot" {:blah "woot"})
              (catch Exception ex (.getMessage ex))))))

(deftest custom-handler-test
  (let [handler (tag-handler
                  (fn [args context-map content]
                    (get-in content [:foo :content]))
                  :foo :endfoo)]
    (is
      (= "some bar content"
         (render-template (parse parse-input (java.io.StringReader. "{% foo %}some {{bar}} content{% endfoo %}")
                                 {:custom-tags {:foo handler}}) {:bar "bar"}))))

  (let [handler (tag-handler
                  (fn [args context-map] (clojure.string/join "," args))
                  :bar)]
    (is (= "arg1,arg2"
           (render-template (parse parse-input (java.io.StringReader. "{% bar arg1 arg2 %}")
                                   {:custom-tags {:bar handler}}) {}))))

  (add-tag! :bar (fn [args context-map] (clojure.string/join "," args)))
  (render-template (parse parse-input (java.io.StringReader. "{% bar arg1 arg2 %}")) {}))

(deftest remove-tag
  (add-tag! :temp (fn [args _] (str "TEMP_" (clojure.string/join "_" (map (comp clojure.string/upper-case str) args)))))
  (is (= "TEMP_ARG1_ARG2" (render "{% temp arg1 arg2 %}" {})))
  (remove-tag! :temp)
  (is (thrown? Exception (render "{% temp arg1 arg2 %}" {}))))

(deftest custom-filter-test
  (is (= "BAR"
         (render-template (parse parse-input (java.io.StringReader. "{{bar|embiginate}}")
                                 {:custom-filters
                                  {:embiginate (fn [^String s] (.toUpperCase s))}}) {:bar "bar"}))))

(deftest passthrough
  (let [s "a b c d"]
    (is (= s (render s {}))))
  (let [s "{{blah}} a b c d"]
    (is (= " a b c d" (render s {}))))
  (let [s "{{blah}} a b c d"]
    (is (= "blah a b c d" (render s {:blah "blah"}))))
  ;; Invalid tags are now ignored ;)
  (let [s "{a b c} \nd"]
    (is (= s (render s {})))))


(deftest inheritance
  (binding
    [*tag-second-pattern* (pattern *tag-second*)
     *filter-open-pattern* (pattern "\\" *tag-open* "\\" *filter-open* "\\s*")
     *filter-close-pattern* (pattern "\\s*\\" *filter-close* "\\" *tag-close*)
     *filter-pattern* (pattern "\\" *tag-open* "\\" *filter-open* "\\s*.*\\s*\\" *filter-close* "\\" *tag-close*)
     *tag-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*.*\\s*\\" *tag-second* "\\" *tag-close*)
     *tag-open-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*")
     *tag-close-pattern* (pattern "\\s*\\" *tag-second* "\\" *tag-close*)
     *include-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*include.*")
     *extends-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*extends.*")
     *block-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*block.*")
     *block-super-pattern* (pattern "\\" *tag-open* "\\" *filter-open* "\\s*block.super\\s*\\" *filter-close* "\\" *tag-close*)
     *endblock-pattern* (pattern "\\" *tag-open* "\\" *tag-second* "\\s*endblock.*")]
    (is
      (= (fix-line-sep "<html>\n<body>{% block header %}\nB header\n\n<h1>child-a header</h1>\n<<\noriginal header\n>>\n\n{% endblock %}\n\n<div>{% block content %}\nSome content\n{% endblock %}</div>\n\n{% block footer %}\n<p>footer</p>\n{% endblock %}</body>\n</html>")
         (preprocess-template "templates/inheritance/child-b.html")))
    (is
      (= "{%ifequal greeting|default:\"Hello!\" name|default:\"Jane Doe\"%} {{greeting|default:\"Hello!\"}} {{name|default:\"Jane Doe\"}} {%endifequal%}"
         (preprocess-template "templates/inheritance/parent.html")))
    (is
      (= (fix-line-sep "<html>\n    <head></head>\n    <body>\n        {% block hello %}\n\n            Hello \n         World\n{% endblock %}\n    </body>\n</html>")
         (preprocess-template "templates/inheritance/super-b.html")))
    (is
      (= (fix-line-sep "<html>\n    <head></head>\n    <body>\n        {% block hello %}\n\n\n            Hello \n         World\nCruel World\n{% endblock %}\n    </body>\n</html>")
         (preprocess-template "templates/inheritance/super-c.html")))
    (is
      (= (fix-line-sep "start a\n{% block a %}{% endblock %}\nstop a\n\n{% block content %}{% endblock %}\n\nHello, {{name}}!\n")
         (preprocess-template "templates/inheritance/inherit-a.html")))
    (is
      (= (fix-line-sep "start a\n{% block a %}\nstart b\n{% block b %}{% endblock %}\nstop b\n{% endblock %}\nstop a\n\n{% block content %}content{% endblock %}\n\nHello, {{name}}!\n")
         (preprocess-template "templates/inheritance/inherit-b.html")))
    (is
      (= (fix-line-sep "start a\n{% block a %}\nstart b\n{% block b %}\nstart c\nstop c\n{% endblock %}\nstop b\n{% endblock %}\nstop a\n\n{% block content %}content{% endblock %}\n\nHello, {{name}}!\n")
         (preprocess-template "templates/inheritance/inherit-c.html")))
    (is
      (= (fix-line-sep "Base template.\n\n\t\n<p></p>\n\n\n")
         (render-file "templates/child.html" {})))
    (is (= "base tempate hello"
           (render-file "templates/inheritance/include-snippet.html" {})))
    (is
      (= (fix-line-sep "Base template.\n\n\t\n<p>blah</p>\n\n\n")
         (render-file "templates/child.html" {:content "blah"})))
    (is
      (= "hello"
         (render "{% if any foo bar baz %}hello{% endif %}" {:bar "foo"})))
    (is
      (= "hello"
         (render "{% if not any foo bar baz %}hello{% endif %}" {})))
    (is
      (= "hello"
         (render "{% if all foo bar %}hello{% endif %}" {:foo "foo" :bar "bar"})))
    (is
      (= ""
         (render "{% if all foo bar %}hello{% endif %}" {:foo "foo"})))
    (is
      (= "hello"
         (render "{% if not all foo bar baz %}hello{% endif %}" {:foo "foo"})))
    (is
      (= ""
         (render "{% if not all foo bar %}hello{% endif %}" {:foo "foo" :bar "bar"})))
    (is
      (= "/page?name=foo - abc" (render-file "templates/include.html" {})))
    (is
      (= "/page?name=foo - xyz" (render-file "templates/include.html" {:gridid "xyz"})))))

(deftest include-in-path-name
  (is
    (= "main template foo body" (render-file "templates/my-include.html" {:foo "foo"}))
    )
  )

(deftest custom-tags
  (is
    (= "<<1>><<2>><<3>>"
       (render "[% for ele in foo %]<<[{ele}]>>[%endfor%]"
               {:foo [1 2 3]}
               {:tag-open  \[
                :tag-close \]})))

  (is
    (= (fix-line-sep "Base template.\n\n\t\n<p></p>\n\n\n")
       (render-file "templates/child-custom.html"
                    {}
                    {:tag-open     \[
                     :tag-close    \]
                     :filter-open  \(
                     :filter-close \)
                     :tag-second   \#}))))

(deftest no-tag
  (is (= "{" (render-file "templates/no_tag.html" {}))))

(deftest tags-validation
  (is
    (= "5" (render-file "templates/tags-test.html" {:business {:employees (range 5)}}))))

(deftest test-now
  (let [date-format "dd MM yyyy"
        formatted-date (.format (java.text.SimpleDateFormat. date-format) (java.util.Date.))]
    (is (= (str "\"" formatted-date "\"") (render (str "{% now \"" date-format "\"%}") {})))))

(deftest test-comment
  (is
    (= "foo bar  blah"
       (render "foo bar {% comment %} baz test {{x}} {% endcomment %} blah" {})))
  (is
    (= "foo bar  blah"
       (render "foo bar {% comment %} baz{% if x %}nonono{%endif%} test {{x}} {% endcomment %} blah" {})))
  (is
    (= "foo bar  blah"
       (render "foo bar {# baz test {{x}} #} blah" {}))))


(deftest test-firstof
  (is (= "x" (render "{% firstof var1 var2 var3 %}" {:var2 "x" :var3 "not me"}))))


(deftest test-verbatim
  (is (= "{{if dying}}Still alive.{{/if}}"
         (render "{% verbatim %}{{if dying}}Still alive.{{/if}}{% endverbatim %}" {})))
  (is (= (fix-line-sep "\n<p class=\"name\">{%=file.name%}</p>\n\n")
         (render-file "templates/verbatim.html" {}))))

(deftest test-with
  (is
    (= "5 employees"
       (render "{% with total=business.employees|count %}{{ total }} employee{{ business.employees|pluralize }}{% endwith %}"
               {:business {:employees (range 5)}})))
  (is
    (= "total:5 employees"
       (render "{% with label=label total = business.employees|count %}{{label}}{{ total }} employee{{ business.employees|pluralize }}{% endwith %}"
               {:label "total:" :business {:employees (range 5)}})))
  (is
    (= "foocorp"
       (render "{% with name=business.name %}{{name}}{% endwith %}"
               {:business {:name "foocorp"}}))))


(deftest test-for
  (is
    (= " s  a "
       (render "{%for x in foo.0%} {{x.id}} {%endfor%}" {:foo [[{:id "s"} {:id "a"}]]})))
  (is
    (= "<ul><li>Sorry, no athletes in this list.</li><ul>"
       (render (str "<ul>"
                    "{% for athlete in athlete_list %}"
                    "<li>{{ athlete.name }}</li>"
                    "{% empty %}"
                    "<li>Sorry, no athletes in this list.</li>"
                    "{% endfor %}"
                    "<ul>")
               {})))
  (is
    (= "1345"
       (render "{% for x in foo.bar|sort %}{{x}}{% endfor %}" {:foo {:bar [1 4 3 5]}})))
  (is
    (= "5431"
       (render "{% for x in foo.bar|sort|sort-reversed %}{{x}}{% endfor %}" {:foo {:bar [1 4 3 5]}})))
  (is
    (= "1,2,a;3,4,;"
       (render "{% for a, b, c in items %}{{a}},{{b}},{{c}};{% endfor %}" {:items [[1 2 "a" "b"] [3 4]]})))
  (is
    (= "1,2,a;3,4,;"
       (render "{% for a,b, c in items %}{{a}},{{b}},{{c}};{% endfor %}" {:items [[1 2 "a" "b"] [3 4]]})))
  (is
    (= "[1 2 &quot;a&quot; &quot;b&quot;],,;[3 4],,;"
       (render "{% for a in items %}{{a}},{{b}},{{c}};{% endfor %}" {:items [[1 2 "a" "b"] [3 4]]})))
  (is
    (= "a,bc,d"
       (render "{% for x,y in items %}{{x}},{{y}}{% endfor %}" {:items [["a" "b"] ["c" "d"]]})))
  (is (= "" (render "{% for i in items %}{{i}}{% endfor %}" {})))
  (is (= "" (render "{% for i in items %}{{i}}{% endfor %}" {:i "foo"})))
  (is (= "" (render "{% for i in items %}{{i}}{% endfor %}" {:items []})))
  (is
    (= "1234567890"
       (render-template
         (parse parse-input (java.io.StringReader.
                              "{% for item in list %}{% for i in item.items %}{{i}}{% endfor %}{% endfor %}"))
         {:list [{:items [1 2 3]} {:items [4 5 6]} {:items [7 8 9 0]}]})))
  (is (= "bob"
         (render-template
           (parse parse-input (java.io.StringReader.
                                "{% for item in list.items %}{{item.name}}{% endfor %}"))
           {:list {:items [{:name "bob"}]}})))
  (is (= (render "{% for ele in foo %}<<{{ele}}>>{%endfor%}"
                 {:foo [1 2 3]})
         "<<1>><<2>><<3>>"))
  (is (= (render "{% for ele in foo %}{{ele}}-{{forloop.counter}}-{{forloop.counter0}}-{{forloop.revcounter}}-{{forloop.revcounter0}};{%endfor%}"
                 {:foo [1 2 3]})
         "1-1-0-2-3;2-2-1-1-2;3-3-2-0-1;")))

(deftest for-filter-test
  (is
    (=
      "barbazfoo"
      (render
        "{% for m in thing|sort-by:@sort-key %}{{m.name}}{% endfor %}"
        {:sort-key :name :thing [{:name "bar"} {:name "foo"} {:name "baz"}]}))))

(deftest nested-for-test
  (is
    (= "<html>\n<body>\n<ul>\n\n\t<li>\n\t\n\ttest\n\t\n\t</li>\n\n\t<li>\n\t\n\ttest1\n\t\n\t</li>\n \n</ul>\n</body>\n</html>"
       (render-template (parse parse-input (str path "nested-for.html"))
                        {:name "Bob" :users [[{:name "test"}] [{:name "test1"}]]}))))

(deftest test-map-lookup
  (is (= (render "{{foo}}" {:foo {:bar 42}})
         "{:bar 42}"))
  (is (= (render "{{foo.bar}}" {:foo {:bar 42}})
         "42")))

(deftest script-style
  (is
    (= "<script src=\"/js/site.js\" type=\"text/javascript\"></script>"
       (render "{% script \"/js/site.js\" %}" {})))
  (is
    (= "<script src=\"/myapp/js/site.js\" type=\"text/javascript\"></script>"
       (render "{% script \"/js/site.js\" %}" {:servlet-context "/myapp"})))
  (is
    (= "<link href=\"/myapp/css/screen.css\" rel=\"stylesheet\" type=\"text/css\" />"
       (render "{% style \"/css/screen.css\" %}" {:servlet-context "/myapp"}))))

(deftest script-async
  (is
    (= "<script async src=\"/js/site.js\" type=\"text/javascript\"></script>"
      (render "{% script \"/js/site.js\" async=\"true\" %}" {})))
  (is
    (= "<script async src=\"/js/site.js\" type=\"text/javascript\"></script>"
      (render "{% script \"/js/site.js\" async=1 %}" {})))
  (is
    (= "<script async src=\"/js/site.js\" type=\"text/javascript\"></script>"
      (render "{% with var = 1 %}{% script \"/js/site.js\" async=var %}{% endwith %}" {})))
  (is
    (= "<script src=\"/js/site.js\" type=\"text/javascript\"></script>"
      (render "{% script \"/js/site.js\" async=nil %}" {}))))

(deftest cycle-test
  (is
    (= "\"foo\"1\"bar\"2\"baz\"1\"foo\"2\"bar\"1"
       (render "{% for i in range %}{% cycle \"foo\" \"bar\" \"baz\" %}{% cycle 1 2 %}{% endfor %}"
               {:range (range 5)}))))

(deftest render-test
  (= "<ul><li>0</li><li>1</li><li>2</li><li>3</li><li>4</li></ul>"
     (render-template (parse parse-input (java.io.StringReader. "<ul>{% for item in items %}<li>{{item}}</li>{% endfor %}</ul>"))
                      {:items (range 5)})))

(deftest nested-forloop-first
  (is (= (render (str "{% for x in list1 %}"
                      "{% for y in list2 %}"
                      "{{x}}-{{y}}"
                      "{% if forloop.first %}'{% endif %} "
                      "{% endfor %}{% endfor %}")
                 {:list1 '[a b c]
                  :list2 '[1 2 3]})
         "a-1' a-2 a-3 b-1' b-2 b-3 c-1' c-2 c-3 ")))

(deftest forloop-with-one-element
  (is (= (render (str "{% for x in list %}"
                      "-{{x}}"
                      "{% endfor %}")
                 {:list '[a]})
         "-a")))

(deftest forloop-with-no-elements
  (is (= (render (str "before{% for x in list %}"
                      "-{{x}}"
                      "{% endfor %}after")
                 {:list '[]})
         "beforeafter")))

(deftest tag-sum-test
  (is
    (= "3"
       (render "{% sum foo %}" {:foo 3})) "sum of Foo solely should be 3")
  (is
    (= "5"
       (render "{% sum foo bar %}" {:foo 2 :bar 3})) "sum of Foo and bar should be 5")
  (is
    (= "6"
       (render "{% sum foo foo %}" {:foo 3})) "sum of Foo twice should be 6")
  (is
    (= "6"
       (render "{% sum foo bar baz %}" {:foo 3 :bar 2 :baz 1})))
  (is
    (= "6"
       (render "{% sum foo bar.baz %}" {:foo 3 :bar {:baz 3}})))
  )

(deftest tag-info-test
  (is
    (= {:args ["i" "in" "nums"], :tag-name :for, :tag-type :expr}
       (read-tag-info (java.io.StringReader. "% for i in nums %}"))))
  (is
    (= {:tag-value "nums", :tag-type :filter}
       (read-tag-info (java.io.StringReader. "{ nums }}")))))

(deftest if-tag-test
  (is
    (= (fix-line-sep "\n\n\n\n<h1>NOT BAR!</h1>\n\n\n\n\"bar\"\n\n\n\n\t\n\tinner\n\t\n")
       (render-template (parse parse-input (str path "if.html")) {:nested "x" :inner "y"})))
  (is
    (= (fix-line-sep "\n\n\n\n<h1>NOT BAR!</h1>\n\n\n\n\"foo\"\n\n\n")
       (render-template (parse parse-input (str path "if.html")) {:user-id "bob"})))
  (is
    (= (fix-line-sep "\n\n\n\n<h1>NOT BAR!</h1>\n\n\n\n\"bar\"\n\n\n")
       (render-template (parse parse-input (str path "if.html")) {:foo false})))
  (is
    (= (fix-line-sep "\n<h1>FOO!</h1>\n\n\n\n\n<h1>NOT BAR!</h1>\n\n\n\n\"bar\"\n\n\n")
       (render-template (parse parse-input (str path "if.html")) {:foo true})))
  (is
    (= (fix-line-sep "\n<h1>FOO!</h1>\n\n\n\n\n<h1>BAR!</h1>\n\n\n\n\"bar\"\n\n\n")
       (render-template (parse parse-input (str path "if.html")) {:foo true :bar "test"})))
  (is
    (= ""
       (render "{% if x > 2 %}bigger{% endif %}" {:v 3})))
  (is
    (= "ok"
       (render "{% if x = 2.0 %}ok{% endif %}" {:x 2}))
    (= "ok"
       (render "{% if x|length = 5 %}ok{% endif %}" {:x (range 5)})))
  (is
    (= "bigger"
       (render "{% if v > 2 %}bigger{% endif %}" {:v 3})))
  (is
    (= ""
       (render "{% if v > 2 %}bigger{% endif %}" {:v 0})))
  (is
    (= "not bigger"
       (render "{% if not v > 2 %}not bigger{% endif %}" {:v 0})))
  (is
    (= "smaller"
       (render "{% if not v > 2 %}bigger{% else %}smaller{% endif %}" {:v 5})))
  (is
    (= "equal"
       (render "{% if 5 = v %}equal{% endif %}" {:v 5})))
  (is
    (= ""
       (render "{% if not 5 = v %}equal{% endif %}" {:v 5})))
  (is
    (= "greater equal"
       (render "{% if 5 <= v %}greater equal{% endif %}" {:v 5})))
  (is
    (= "less equal"
       (render "{% if 5 >= v %}less equal{% endif %}" {:v 5})))
  (is
    (= (render "{% if v1 >= v2 %}less equal{% endif %}" {:v1 5 :v2 3})))
  (is
    (= " no value "
       (render "{% if user-id %} has value {% else %} no value {% endif %}" {})))
  (is (= (render "{% if foo %}foo is true{% endif %}" {:foo true})
         "foo is true"))
  (is (= (render "{% if foo %}foo is true{% endif %}" {:foo false})
         ""))
  (is (= (render "{% if foo %}foo is true{% else %}foo is false{% endif %}"
                 {:foo true})
         "foo is true"))
  (is (= (render "{% if foo %}foo is true{% else %}foo is false{% endif %}"
                 {:foo false})
         "foo is false"))

  (let [template
        (parse parse-input
               (java.io.StringReader.
                 "{% if foo %}
                  foo is true
                  {% if bar %}bar is also true{% endif %}
                  {% else %} foo is false
                  {% if baz %}but baz is true {% else %}baz is also false{% endif %}
                  {% endif %}"))]
    (is (= (render-template template {:foo true :bar true :baz false})
           "\n                  foo is true\n                  bar is also true\n                  "))
    (is (= (render-template template {:foo false :bar true :baz false})
           " foo is false\n                  baz is also false\n                  "))
    (is (= (render-template template {:foo false :bar true :baz true})
           " foo is false\n                  but baz is true \n                  ")))
  (is (thrown? Exception (render "foo {% else %} bar" {}))))

(deftest test-if-not
  (is (= (render "{% if not foo %}foo is true{% endif %}" {:foo true})
         ""))
  (is (= (render "{% if not foo %}foo is true{% endif %}" {:foo false})
         "foo is true")))

(deftest test-nested-if
  (is (= (render (str "{% if foo %}before bar {% if bar %}"
                      "foo & bar are true"
                      "{% endif %} after bar{% endif %}")
                 {:foo true
                  :bar true})
         "before bar foo & bar are true after bar")))

(deftest ifequal-tag-test
  (= "\n<h1>equal!</h1>\n\n\n\n<p>not equal</p>\n"
     (render-template (parse parse-input (str path "ifequal.html")) {:foo "bar"}))
  (= "\n\n<h1>equal!</h1>\n\n\n<p>not equal</p>\n"
     (render-template (parse parse-input (str path "ifequal.html")) {:foo "baz" :bar "baz"}))
  (= "\n\n<h1>equal!</h1>\n\n\n<h1>equal!</h1>\n"
     (render-template (parse parse-input (str path "ifequal.html")) {:baz "test"}))
  (= "\n\n<h1>equal!</h1>\n\n\n<p>not equal</p>\n"
     (render-template (parse parse-input (str path "ifequal.html")) {:baz "fail"}))

  (is (= (render "{% ifequal foo|upper \"FOO\" %}yez{% endifequal %}" {:foo "foo"})
         "yez"))

  (is (= (render "{% ifequal foo \"foo\" %}yez{% endifequal %}" {:foo "foo"})
         "yez"))
  (is (= (render "{% ifequal foo \"foo\" bar %}yez{% endifequal %}"
                 {:foo "foo"
                  :bar "foo"})
         "yez"))
  (is (= (render "{% ifequal foo \"foo\" bar %}yez{% endifequal %}"
                 {:foo "foo"
                  :bar "bar"})
         ""))
  (is (= (render "{% ifequal foo \"foo\" %}foo{% else %}no foo{% endifequal %}"
                 {:foo "foo"})
         "foo"))
  (is (= (render "{% ifequal foo \"foo\" %}foo{% else %}no foo{% endifequal %}"
                 {:foo false})
         "no foo"))
  (is (= (render "{% ifequal foo :foo %}foo{% endifequal %}"
                 {:foo :foo})
         "foo")))

(deftest ifunequal-tag-test
  (is (= (render "{% ifunequal foo \"bar\" %}yez{% endifunequal %}" {:foo "foo"})
         "yez"))
  (is (= (render "{% ifunequal foo \"foo\" %}yez{% endifunequal %}" {:foo "foo"})
         ""))
  (is (= (render "{% ifunequal foo|upper \"foo\" %}yez{% endifunequal %}" {:foo "foo"})
         "yez"))
  (is (= (render "{% ifunequal foo :bar %}foo{% endifunequal %}"
                 {:foo :foo})
         "foo")))

(deftest safe-tag
  (is (= (render "{% safe %} {% if bar %}{% for i in y %} {{foo|upper}} {% endfor %}{%endif%} {% endsafe %}"
                 {:bar true :foo "<foo>" :y [1 2]})
         "  <FOO>  <FOO>  "))
  (is (= (render-file "templates/safe.html" {:bar true :unsafe "<script>window.location.replace('http://not.so.safe');</script>"})
         "<script>window.location.replace('http://not.so.safe');</script>")))

(deftest filter-tag-test
  (is
    (= "ok"
       ((filter-tag {:tag-value "foo.bar.baz"}) {:foo {:bar {:baz "ok"}}})))
  (is
    (= "ok"
       ((filter-tag {:tag-value "foo"}) {:foo "ok"}))))

(deftest tag-content-test
  (is
    (= {:if   {:args nil :content ["foo bar "]}
        :else {:args nil :content [" baz"]}}
       (into {}
             (map
               (fn [[k v]]
                 [k (update-in v [:content] #(map (fn [node] (.render-node ^selmer.node.INode node {})) %))])
               (tag-content (java.io.StringReader. "foo bar {%else%} baz{% endif %}") :if :else :endif)))))
  (is
    (= {:for {:args nil, :content ["foo bar  baz"]}}
       (update-in (tag-content (java.io.StringReader. "foo bar  baz{% endfor %}") :for :endfor)
                  [:for :content 0] #(.render-node ^selmer.node.INode % {})))))

(deftest filter-upper
  (is (= "FOO" (render "{{f|upper}}" {:f "foo"}))))


(deftest filter-take
  (is (= "[:dog :cat :bird]"
         (render "{{seq-of-some-sort|take:3}}" {:seq-of-some-sort [:dog :cat :bird :bird :bird :is :the :word]}))))


(deftest filter-drop
  (is (= "[:bird :is :the :word]"
         (render "{{seq-of-some-sort|drop:4}}" {:seq-of-some-sort [:dog :cat :bird :bird :bird :is :the :word]}))))

(deftest filter-drop-formatted
  (is (= "bird is the word"
         (render "{{seq-of-some-sort|drop:4|join:\" \"}}" {:seq-of-some-sort ["dog" "cat" "bird" "bird" "bird" "is" "the" "word"]}))))

;; How do we handle nils ?
;; nils should return empty strings at the point of injection in a DTL library. - cma
(deftest filter-no-value
  (is (= "" (render "{{f|upper}}" {}))))

(deftest filter-currency-format
  (let [amount 123.45
        curr (java.text.NumberFormat/getCurrencyInstance (Locale/getDefault))
        curr-de (java.text.NumberFormat/getCurrencyInstance (java.util.Locale. "de"))
        curr-de-DE (java.text.NumberFormat/getCurrencyInstance (java.util.Locale. "de" "DE"))]
    (is (= (.format curr amount)
           (render "{{f|currency-format}}" {:f amount})))
    (is (= (.format curr-de amount) (render "{{f|currency-format:de}}" {:f amount})))
    (is (= (.format curr-de-DE amount) (render "{{f|currency-format:de:DE}}" {:f amount})))))

(deftest filter-number-format
  (let [number 123.04455
        numberformat "%.3f"
        locale (Locale/getDefault)
        locale-de (java.util.Locale. "de")]
    (is (= (String/format locale numberformat (into-array Object [number]))
           (render (str "{{f|number-format:" numberformat "}}") {:f number})))
    (is (= (String/format locale-de numberformat (into-array Object [number]))
           (render (str "{{f|number-format:" numberformat ":de}}") {:f number})))))

(deftest filter-date
  (let [date (java.util.Date.)
        firstofmarch (java.util.Date. 2014 2 1)]
    (is (= "" (render "{{d|date:\"yyyy-MM-dd\"}}" {:d nil})))
    (is (= (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") date)
           (render "{{f|date:\"yyyy-MM-dd HH:mm:ss\"}}" {:f date})))
    (is (= (.format (java.text.SimpleDateFormat. "MMMM" (java.util.Locale. "fr")) firstofmarch)
           (render "{{f|date:\"MMMM\":fr}}" {:f firstofmarch})))))

(deftest filter-hash-md5
  (is (= "acbd18db4cc2f85cedef654fccc4a4d8"
         (render "{{f|hash:\"md5\"}}" {:f "foo"}))))

(deftest filter-hash-sha512
  (is (= (str "f7fbba6e0636f890e56fbbf3283e524c6fa3204ae298382d624741d"
              "0dc6638326e282c41be5e4254d8820772c5518a2c5a8c0c7f7eda19"
              "594a7eb539453e1ed7")
         (render "{{f|hash:\"sha512\"}}" {:f "foo"}))))

(deftest filter-hash-invalid-hash
  (is (thrown? Exception (render "{{f|hash:\"foo\"}}" {:f "foo"}))))

(deftest filter-join
  (is (= "1, 2, 3, 4"
         (render "{{sequence|join:\", \"}}" {:sequence [1 2 3 4]})))
  (is (= "1234"
         (render "{{sequence|join}}" {:sequence [1 2 3 4]}))))

(deftest filter-add
  (is (= "11" (render "{{add_me|add:2:3:4}}" {:add_me 2})))
  (is (= "hello" (render "{{h|add:e:l:l:o}}" {:h "h"})))
  (is (= "0" (render "{{paginate.page|add:-1}}" {:paginate {:page 1}}))))

(deftest filter-count
  (is (= "3" (render "{{f|count}}" {:f "foo"})))
  (is (= "4" (render "{{f|count}}" {:f [1 2 3 4]})))
  (is (= "0" (render "{{f|count}}" {:f []})))
  (is (= "0" (render "{{f|count}}" {}))))

(deftest emptiness
  (is (= "true" (render "{{xs|empty?" {:xs []})))
  (is (= "foo" (render "{% if xs|empty? %}foo{% endif %}" {:xs []})))
  (is (= "" (render "{% if xs|not-empty %}foo{% endif %}" {:xs []})))
  (is (= "foo" (render "{% if xs|not-empty %}foo{% endif %}" {:xs [1 2]}))))

;; switched commas + doublequotes for colons
;; TODO - maybe remain consistent with django's only 1 argument allowed.
;; I like being able to accept multiple arguments.
;; Alternatively, we could have curried filters and just chain
;; it into a val and apply it Haskell-style.
;; I think that could surprise users. (which is bad)
(deftest filter-pluralize
  (is (= "s" (render "{{f|pluralize}}" {:f []})))
  (is (= "" (render "{{f|pluralize}}" {:f [1]})))
  (is (= "s" (render "{{f|pluralize}}" {:f [1 2 3]})))

  (is (= "ies" (render "{{f|pluralize:\"ies\"}}" {:f []})))
  (is (= "" (render "{{f|pluralize:\"ies\"}}" {:f [1]})))
  (is (= "ies" (render "{{f|pluralize:\"ies\"}}" {:f [1 2 3]})))

  (is (= "ies" (render "{{f|pluralize:y:ies}}" {:f []})))
  (is (= "y" (render "{{f|pluralize:y:ies}}" {:f [1]})))
  (is (= "ies" (render "{{f|pluralize:y:ies}}" {:f [1 2 3]})))

  (is (= "s" (render "{{f|pluralize}}" {:f 0})))
  (is (= "" (render "{{f|pluralize}}" {:f 1})))
  (is (= "s" (render "{{f|pluralize}}" {:f 3})))

  (is (= "ies" (render "{{f|pluralize:\"ies\"}}" {:f 0})))
  (is (= "" (render "{{f|pluralize:\"ies\"}}" {:f 1})))
  (is (= "ies" (render "{{f|pluralize:\"ies\"}}" {:f 3})))

  (is (= "ies" (render "{{f|pluralize:y:ies}}" {:f 0})))
  (is (= "y" (render "{{f|pluralize:y:ies}}" {:f 1})))
  (is (= "ies" (render "{{f|pluralize:y:ies}}" {:f 3}))))

;; to-json is simply json here
(deftest filter-to-json
  (is (= "1" (render "{{f|json}}" {:f 1})))
  (is (= "[1]" (render "{{f|json}}" {:f [1]})))
  (is (= {"dan" "awesome", "foo" 27}
         (-> ^String (render "{{f|json}}" {:f {:foo 27 :dan "awesome"}})
             (.replaceAll "&quot;" "\"")
             parse-string)))
  (is (= {"dan" "awesome", "foo" 27}
         (parse-string (render "{{f|json|safe}}" {:f {:foo 27 :dan "awesome"}}))))
  ;; safe only works at the end
  #_(is (= "{\"foo\":27,\"dan\":\"awesome\"}"
           (render "{{f|safe|json}}" {:f {:foo 27 :dan "awesome"}})))
  ;; Do we really want to nil-pun the empty map?
  ;; Is that going to surprise the user?
  (is (= "null" (render "{{f|json}}" {}))))

;; TODO
(deftest filter-chaining
  (is (= "ACBD18DB4CC2F85CEDEF654FCCC4A4D8"
         (render "{{f|hash:\"md5\"|upper}}" {:f "foo"}))))

(deftest test-escaping
  (is (= "<tag>&lt;foo bar=&quot;baz&quot;&gt;\\&gt;</tag>"
         (render "<tag>{{f}}</tag>" {:f "<foo bar=\"baz\">\\>"})))
  ;; Escapes the same chars as django's escape
  (is (= "&amp;&quot;&#39;&lt;&gt;"
         (render "{{f}}" {:f "&\"'<>"})))
  ;; Escapes content that is supposed to be URL encoded
  (is (= "clojure+url"
         (render "{{f|urlescape}}" {:f "clojure url"}))))

;; Safe only works at the end.
;; Don't think it should work anywhere else :-) - cbp (agreed, - cma)
(deftest test-safe-filter
  (is (= "&lt;foo&gt;"
         (render "{{f}}" {:f "<foo>"})))
  (is (= "<foo>"
         (render "{{f|safe}}" {:f "<foo>"})))
  (is (= "<FOO>"
         (render "{{f|upper|safe}}" {:f "<foo>"}))))

;; test @-syntax for dereferencing context map in filter arguments
(deftest test-deref-filter-arg
  (is (= " Sean "                                           ;; note center filter expects String for width!
         (render "{{name|center:@width}}" {:name "Sean" :width "6"})))
  (is (= "4"                                                ;; ensure we can substitute a data structure
         (render "{{name|default:@v|count}}" {:v [1 2 3 4]})))
  (is (= "@"                                                ;; literal @ is not dereferenced
         (render "{{name|default:@}}" {:name nil})))
  (is (= "@foo"                                             ;; literal @foo used when no context map match
         (render "{{name|default:@foo}}" {:name nil})))
  (is (= "quux"                                             ;; test nested lookup
         (render "{{name|default:@foo.bar.baz}}" {:name nil :foo {:bar {:baz "quux"}}}))))

(deftest custom-resource-path-setting
  (is nil? *custom-resource-path*)
  (do
    (set-resource-path! "/some/path")
    (is (= "file:////some/path/" *custom-resource-path*)))
  (do (set-resource-path! "/any/other/path/")
      (is (= "file:////any/other/path/" *custom-resource-path*)))
  (do (set-resource-path! "file:////any/other/path/")
    (is (= "file:////any/other/path/" *custom-resource-path*)))
  (set-resource-path! nil)
  (is nil? *custom-resource-path*))

(deftest custom-resource-path-setting-url
  (set-resource-path! (clojure.java.io/resource "templates/inheritance"))
  (is (string? *custom-resource-path*))
  (is (= (fix-line-sep "Hello, World!\n") (render-file "foo.html" {:name "World"})))
  (set-resource-path! nil))

(deftest safe-filter
  (add-filter! :foo (fn [^String x] [:safe (.toUpperCase x)]))
  (is
    (= "<DIV>I'M SAFE</DIV>"
       (render "{{x|foo}}" {:x "<div>I'm safe</div>"})))
  (add-filter! :bar #(.toUpperCase ^String %))
  (is
    (= "&lt;DIV&gt;I&#39;M NOT SAFE&lt;/DIV&gt;"
       (render "{{x|bar}}" {:x "<div>I'm not safe</div>"}))))

(deftest remove-filter
  (testing "we can add and remove a filter"
    (add-filter! :temp (fn [x] (str "TEMP_" (clojure.string/upper-case x))))
    (is (= "TEMP_FOO_BAR" (render "{{x|temp}}" {:x "foo_bar"})))
    (remove-filter! :temp)
    (is (thrown? Exception (render "{{x|temp}}" {:x "foo_bar"})))))

(deftest linebreaks-test
  (testing "single newlines become <br />, double newlines become <p>"
    (is (= "<p><br />bar<br />baz</p>"
           (render "{{foo|linebreaks|safe}}" {:foo "\nbar\nbaz"})))))

(deftest linebreaks-br-test
  (testing "works like linebreaks, but no <p> tags"
    (is (= "<br />bar<br />baz"
           (render "{{foo|linebreaks-br|safe}}" {:foo "\nbar\nbaz"})))))

(deftest linenumbers-test
  (testing "displays text with line numbers"
    (is (= "1. foo\n2. bar\n3. baz"
           (render "{{foo|linenumbers}}" {:foo "foo\nbar\nbaz"})))))

(deftest lower-test
  (testing "converts words to lower case"
    (is (= "foobar" (render "{{foo|lower}}" {:foo "FOOBaR"})))
    (is (= "foobar" (render "{{foo|lower}}" {:foo "foobar"})))))

(deftest literals-test
  (testing "converts words to lower case"
    (is (= "foobar" (render "{{\"FOObar\"|lower}}" {})))
    (is (= "10" (render "{{1|add:2:3:4}}" {})))))

(deftest number-format-test
  (testing "formats the number with default locale"
    (let [locale-number (String/format (Locale/getDefault) "%.3f"
                                       (into-array Object [123.045]))]
      (is (= locale-number (render "{{amount|number-format:%.3f}}" {:amount 123.04455})))
      (is (= locale-number (render "{{amount|number-format:%.3f}}" {:amount 123.045})))))
  (testing "formats the number with specified locale"
    (is (= "123,045" (render "{{amount|number-format:%.3f:de}}" {:amount 123.04455})))))

(deftest default-if-empty-test
  (testing "default when empty behavior"
    (is (= "yogthos" (render "{{name|default-if-empty:\"I <3 ponies\"}}" {:name "yogthos"})))
    (is (= "I &lt;3 ponies" (render "{{name|default-if-empty:\"I <3 ponies\"}}" {:name nil})))
    (is (= "I &lt;3 ponies" (render "{{name|default-if-empty:\"I <3 ponies\"}}" {:name []})))
    (is (= "I &lt;3 ponies" (render "{{name|default-if-empty:\"I <3 ponies\"}}" {})))))

(deftest turn-off-escaping-test
  (testing "with escaping turned off"
    (try
      (turn-off-escaping!)
      (is (= "I <3 ponies" (render "{{name}}" {:name "I <3 ponies"})))
      (is (= "I <3 ponies" (render "{{name|default-if-empty:\"I <3 ponies\"}}" {})))
      (is (= "I <3 ponies" (render "{{name|default-if-empty:\"I <3 ponies\"|safe}}" {})))
      (finally (turn-on-escaping!)))))

(deftest without-escaping-test
  (testing "without-escaping macro"
    (without-escaping
      (is (= "I <3 ponies" (render "{{name}}" {:name "I <3 ponies"}))))
    ;; ensure escaping is on after the macro.
    (is (= "<tag>&lt;foo bar=&quot;baz&quot;&gt;\\&gt;</tag>"
           (render "<tag>{{f}}</tag>" {:f "<foo bar=\"baz\">\\>"})))))

(deftest with-escaping-test
  (testing "with-escaping macro when turn-off-escaping! has been called"
    (try
      (turn-off-escaping!)
      (is (= "I <3 ponies" (render "{{name}}" {:name "I <3 ponies"})))
      (with-escaping
        (is (= "<tag>&lt;foo bar=&quot;baz&quot;&gt;\\&gt;</tag>"
               (render "<tag>{{f}}</tag>" {:f "<foo bar=\"baz\">\\>"}))))
      (is (= "I <3 ponies" (render "{{name}}" {:name "I <3 ponies"})))
      (finally (turn-on-escaping!)))))

(deftest name-test
  (testing "converts keywords to strings"
    (is (= "foobar" (render "{{foo|name}}" {:foo :foobar}))))
  (testing "leaves strings as they are"
    (is (= "foobar" (render "{{foo|name}}" {:foo "foobar"})))))

(deftest handler-metadata
  (testing "puts tag into FunctionNode handlers"
    (is (= {:tag {:tag-type :filter, :tag-value "foo"}}
           (as-> (parse-input (java.io.StringReader. "{{foo}}")) $
             (first $)
             (.handler ^selmer.node.FunctionNode $)
             (meta $))))))

(deftest missing-values
  (testing "Missing value - default behaviour"
    (is (= "" (render "{{missing}}" {})))
    (is (= "" (render "{{missing.too}}" {} ""))))

  (testing "Missing value - with custom missing value handlers"
    ;; Using bindings instead of set-missing-value-formatter! to avoid cleanup
    (binding [*missing-value-formatter* (constantly "XXX")
              *filter-missing-values* false]
      (is (= "XXX" (render "{{missing}}" {})))
      (is (= "XXX" (render "{{missing.too}}" {}))))
    (binding [*missing-value-formatter* (fn [tag context-map]
                                          (if (= (:tag-type tag) :filter)
                                            (str "<missing value: " (:tag-value tag) ">")
                                            (str "<missing value: " (:tag-name tag) ">")))
              *filter-missing-values* false]
      (is (= "Hi <missing value: name>" (render "Hi {{name}}" {}))
          (= "Hi mr. <missing value: name.lastname>" (render "Hi mr. {{name.lastname}}" {})))

      (let [custom-tag-handler (tag-handler
                                 (fn [_ context-map]
                                   (when-let [l (:list context-map)]
                                     (clojure.string/join ", " (:list context-map))))
                                 :bar)]
        (is (= "1, 2, 3, 4"
               (render-template
                 (parse parse-input (java.io.StringReader. "{% bar %}") {:custom-tags {:bar custom-tag-handler}})
                 {:list [1 2 3 4]})))
        (is (= "<missing value: :bar>"
               (render-template
                 (parse parse-input (java.io.StringReader. "{% bar %}") {:custom-tags {:bar custom-tag-handler}})
                 {}))))

      (is (= "<missing value: name|count>" (render "{{name|count}}" {})))))

  (testing "Missing value - custom missing value handler with filtering of missing values turned on"
    (binding [*missing-value-formatter* (constantly "XXX")
              *filter-missing-values* true]
      (is (= "XXX" (render "{{missing}}" {})))
      (is (= "XXX" (render "{{missing.too}}" {})))
      (is (= "0" (render "{{missing|count}}" {}))))))
